package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controla as tentativas de login.
 *
 * O bloqueio existe em dois niveis: por usuario e por IP, porque sao dois
 * ataques diferentes - um mira numa conta especifica, o outro mira no
 * sistema inteiro.
 *
 * Os contadores ficam em memoria. Um banco resolveria o problema de perder
 * o estado no restart, mas custaria uma escrita a cada tentativa - e
 * justamente a tentativa de login que o atacante faz em rajada que
 * precisaria aguentar essa carga.
 */
@Service
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Separador entre as partes da chave. Precisa ser um caracter que
     * nunca apareca num nome de usuario nem num IP.
     */
    private static final String SEPARADOR = "|";

    /**
     * Contadores de falhas.
     *
     * As chaves tem dois formatos:
     *   "ip|usuario" - falhas desse usuario vindo desse IP;
     *   "ip|ip"      - falhas totais desse IP, somando todos os usuarios.
     *
     * E um ConcurrentHashMap porque a API atende varias requisicoes ao
     * mesmo tempo (o Tomcat usa um pool de threads) e dois acessos
     * simultaneos a um HashMap comum corromperiam a estrutura.
     */
    private final Map<String, RegistroTentativa> tentativas = new ConcurrentHashMap<>();

    /**
     * Liga/desliga o controle de tentativas.
     * Vem de app.security.rate-limit.enabled (padrao: ligado).
     */
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean habilitado;

    /**
     * Quantas falhas do mesmo usuario (no mesmo IP) o sistema aceita antes
     * de bloquear. Padrao: 5.
     */
    @Value("${app.security.rate-limit.max-attempts:5}")
    private int maxTentativasPorUsuario;

    /**
     * Quantas falhas vindas do mesmo IP (somando todos os usuarios) o
     * sistema aceita antes de bloquear o IP inteiro. Padrao: 20.
     */
    @Value("${app.security.rate-limit.max-attempts-per-ip:20}")
    private int maxTentativasPorIp;

    /**
     * Duracao da janela, em minutos.
     * Depois de 15 minutos sem nova falha, o contador zera sozinho.
     * Padrao: 15 minutos.
     */
    @Value("${app.security.rate-limit.window-minutes:15}")
    private long janelaMinutos;

    /**
     * Registra uma tentativa de autenticacao que deu errado.
     *
     * Chamado pelo AuthenticationAuditHandler sempre que o Spring Security
     * recusa as credenciais.
     *
     * @param ip      endereco IP de origem da requisicao
     * @param usuario nome de usuario informado na tentativa
     */
    public void registrarFalha(String ip, String usuario) {

        if (!habilitado) {
            return;
        }

        // Incrementa o contador do par (usuario, IP).
        incrementar(chaveUsuario(ip, usuario));

        // Incrementa o contador do IP (somando todos os usuarios).
        incrementar(chaveIp(ip));

        LOG.warn("Tentativa de autenticacao FALHOU. usuario={} ip={} "
                        + "falhas_usuario={} falhas_ip={}",
                usuario, ip, contar(chaveUsuario(ip, usuario)), contar(chaveIp(ip)));
    }
    /**
     * Registra uma autenticacao que deu certo.
     *
     * Chamado pelo AuthenticationSuccessAuditListener sempre que o Spring
     * Security aceitou as credenciais.
     *
     * Um login bem-sucedido zera o contador de falhas do usuario. O
     * zeraimento vale pro usuario inteiro, de qualquer IP: quem acabou de
     * provar que sabe a senha nao precisa ficar preso por tentativas
     * antigas, nem por tentativas vindas de outro lugar.
     *
     * O contador do IP nao e zerado, porque ele existe pra medir o volume
     * total de ataques, e nao o historico de um usuario.
     *
     * @param usuario nome de usuario autenticado
     */
    public void registrarSucesso(String usuario) {

        if (!habilitado) {
            return;
        }

        // Tira todos os registros (de qualquer IP) que pertencem a este
        // usuario. O removeIf e seguro mesmo com acesso concorrente ao mapa.
        String sufixo = SEPARADOR + (usuario == null ? "" : usuario);

        tentativas.entrySet().removeIf(entry ->
                entry.getKey().endsWith(sufixo) && !entry.getKey().startsWith("ip" + SEPARADOR));

        LOG.info("Tentativa de autenticacao BEM-SUCEDIDA. usuario={}", usuario);
    }

    /**
     * Diz se o IP esta bloqueado agora.
     *
     * Chamado antes da autenticacao, no BruteForceProtectionFilter. Se o
     * IP ja estourou o limite global, nem vale a pena tentar validar a
     * senha: responder 429 na hora economiza CPU (o hash de senha e caro
     * de proposito) e encurta o ataque.
     *
     * @param ip endereco IP de origem
     * @return true se o IP deve ser bloqueado
     */
    public boolean ipBloqueado(String ip) {

        if (!habilitado) {
            return false;
        }

        RegistroTentativa registro = registrosAtivos().get(chaveIp(ip));

        return registro != null && registro.contador.get() >= maxTentativasPorIp;
    }

    /**
     * Diz se o par (usuario, IP) esta bloqueado.
     *
     * @param ip      endereco IP de origem
     * @param usuario nome de usuario informado
     * @return true se o usuario deve ter o acesso negado temporariamente
     */
    public boolean usuarioBloqueado(String ip, String usuario) {

        if (!habilitado) {
            return false;
        }

        RegistroTentativa registro = registrosAtivos().get(chaveUsuario(ip, usuario));

        return registro != null && registro.contador.get() >= maxTentativasPorUsuario;
    }

    /**
     * Calcula quantos segundos faltam para o bloqueio expirar.
     *
     * Serve pra montar o cabecalho HTTP "Retry-After", que diz ao cliente
     * "tente de novo em N segundos". E bom pra UX e pra seguranca: o
     * cliente sabe o tempo de espera em vez de martelar o servidor.
     *
     * @param ip endereco IP de origem
     * @return segundos restantes (0 se nao houver bloqueio)
     */
    public long segundosAteLiberar(String ip) {

        RegistroTentativa registro = registrosAtivos().get(chaveIp(ip));

        if (registro == null) {
            return 0;
        }

        long restante = Duration.between(Instant.now(), registro.expiraEm).getSeconds();

        return Math.max(restante, 0);
    }

    /**
     * Calcula quantos segundos faltam para o bloqueio deste usuario
     * expirar.
     *
     * Olha primeiro o registro do par (usuario, IP) e, se nao houver, cai
     * para o registro do IP. A ordem importa porque o bloqueio do usuario
     * e o mais especifico, e portanto o que o cliente precisa esperar.
     *
     * @param ip      endereco IP de origem
     * @param usuario nome de usuario informado
     * @return segundos restantes (0 se nao houver bloqueio)
     */
    public long segundosAteLiberar(String ip, String usuario) {

        Map<String, RegistroTentativa> registros = registrosAtivos();

        RegistroTentativa doUsuario = registros.get(chaveUsuario(ip, usuario));

        if (doUsuario != null) {
            long restante = Duration.between(Instant.now(), doUsuario.expiraEm).getSeconds();
            return Math.max(restante, 0);
        }

        RegistroTentativa doIp = registros.get(chaveIp(ip));

        if (doIp == null) {
            return 0;
        }

        long restante = Duration.between(Instant.now(), doIp.expiraEm).getSeconds();

        return Math.max(restante, 0);
    }

    /**
     * Zera todos os contadores.
     * Usado nos testes e como ponto de partida caso a aplicacao precise
     * liberar todo mundo (por exemplo, depois de um alarme falso).
     */
    public void limpar() {
        tentativas.clear();
    }

    /**
     * Incrementa o contador de uma chave e renova a validade da janela.
     */
    private void incrementar(String chave) {

        Instant agora = Instant.now();

        // compute() calcula e atualiza o contador de forma atomica. Sem
        // isso, duas threads que chegassem ao mesmo tempo poderiam
        // sobrescrever uma a outra e perder uma tentativa.
        tentativas.compute(chave, (k, existente) -> {
            if (existente == null || existente.expirada(agora)) {
                // primeira tentativa (ou a janela anterior ja venceu)
                return new RegistroTentativa(1, agora.plus(Duration.ofMinutes(janelaMinutos)));
            }
            existente.contador.incrementAndGet();
            // Cada nova falha renova a janela: e assim que o controle
            // "deslizante" funciona (o atacante tem 5 falhas a cada 15
            // minutos, nao 5 no total pra sempre).
            existente.expiraEm = agora.plus(Duration.ofMinutes(janelaMinutos));
            return existente;
        });
    }

    /**
     * Le a quantidade de falhas de uma chave (0 se nao houver registro).
     */
    private int contar(String chave) {
        RegistroTentativa registro = registrosAtivos().get(chave);
        return registro == null ? 0 : registro.contador.get();
    }

    /**
     * Devolve os registros que ainda estao dentro da janela de tempo.
     * Joga fora da memoria os que ja venceram (limpeza em tempo de
     * acesso, pra nao precisar de um agendador).
     */
    private Map<String, RegistroTentativa> registrosAtivos() {

        Instant agora = Instant.now();

        // removeIf apaga do mapa as entradas vencidas.
        tentativas.entrySet().removeIf(entry -> entry.getValue().expirada(agora));

        return tentativas;
    }

    /**
     * Monta a chave do par (usuario, IP).
     * O IP vem primeiro pra deixar a chave legivel no log.
     */
    private String chaveUsuario(String ip, String usuario) {
        return ip + SEPARADOR + (usuario == null ? "" : usuario);
    }

    /**
     * Monta a chave do IP (contagem agregada de todas as tentativas).
     */
    private String chaveIp(String ip) {
        return "ip" + SEPARADOR + ip;
    }

    /**
     * Tarefa agendada que limpa periodicamente os registros expirados.
     *
     * A limpeza em tempo de acesso (registrosAtivos) ja resolve o
     * crescimento da estrutura. Esta tarefa e a rede de seguranca: garante
     * que a memoria seja liberada mesmo quando ninguem mais faz
     * requisicao (de madrugada, com o servidor ocioso e o contador
     * parado).
     *
     * A anotacao @Scheduled exige que a aplicacao tenha @EnableScheduling.
     * Ver LeilaoApplication.
     */
    @Scheduled(fixedDelayString = "${app.security.rate-limit.cleanup-interval-ms:60000}")
    public void limparExpirados() {

        int antes = tentativas.size();
        registrosAtivos();

        if (antes != tentativas.size()) {
            LOG.debug("Limpeza do controle de tentativas: {} registros expirados removidos.",
                    antes - tentativas.size());
        }
    }

    /**
     * Registro de tentativas (classe interna).
     *
     * Junta o contador (quantas falhas) com a validade (ate quando vale).
     * O contador e um AtomicInteger porque varias threads podem falhar ao
     * mesmo tempo no mesmo par (usuario, IP).
     */
    private static class RegistroTentativa {

        /** Quantidade de falhas registradas dentro da janela atual. */
        private final AtomicInteger contador;

        /** Instante em que a janela expira (a contagem zera). */
        private volatile Instant expiraEm;

        /**
         * Construtor do registro.
         *
         * @param contador valor inicial do contador
         * @param expiraEm instante de expiracao da janela
         */
        RegistroTentativa(int contador, Instant expiraEm) {
            this.contador = new AtomicInteger(contador);
            this.expiraEm = expiraEm;
        }

        /**
         * Diz se a janela de tempo desta tentativa ja venceu.
         *
         * @param agora instante de referencia
         * @return true se o registro pode ser descartado
         */
        boolean expirada(Instant agora) {
            return agora.isAfter(expiraEm);
        }
    }
}
