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
 * ============================================================================
 *  SERVICO: CONTROLE DE TENTATIVAS DE LOGIN (ANTI-FORCA-BRUTA)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O PROBLEMA QUE ESTE SERVICO RESOLVE
 *  ------------------------------------------------------
 *  A autenticacao HTTP Basic compara a senha com o hash BCrypt. O BCrypt e
 *  lento DE PROPOSITO - e o que torna caro um ataque de forca bruta. Mas
 *  "caro" nao e "impossivel": um atacante com um dicionario de senhas
 *  comuns pode disparar milhares de tentativas por minuto. Sem nenhum
 *  controle, o ataque seria detectado apenas quando alguém notasse no log.
 *
 *  Este servico implementa o controle classico de "conta bloqueada
 *  temporariamente": conta as falhas de autenticacao em uma janela de
 *  tempo e bloqueia novas tentativas quando o limite e estourado.
 *
 *  E o item A07:2021 da OWASP Top 10 ("Identification and Authentication
 *  Failures"), que descreve exatamente este cenario.
 *
 *  ------------------------------------------------------
 *  AS DUAS "DIMENSOES" DO CONTROLE
 *  ------------------------------------------------------
 *  O bloqueio e feito em dois niveis, porque os dois Attacks sao reais:
 *
 *  1) POR USUARIO + IP (limite menor, ex.: 5 falhas)
 *     Protege a conta de um usuario especifico. Se alguem esta tentando
 *     adivinhar a senha do "admin", a conta e bloqueada mesmo que o
 *     atacante troque de IP a cada tentativa.
 *
 *  2) POR IP (limite maior, ex.: 20 falhas)
 *     Protege a APLICACAO. Sem esse limite, um atacante poderia tentar
 *     5 senhas para 1000 usuarios diferentes a partir do mesmo IP - o
 *     limite por usuario nunca seria estourado, mas a API receberia um
 *     volume absurdo de tentativas (e o BCrypt, lento, consumiria CPU).
 *
 *  A combinacao dos dois e o que fecha o problema: a conta fica protegida
 *  E o servidor fica protegido.
 *
 *  ------------------------------------------------------
 *  POR QUE OS DADOS FICAM EM MEMORIA?
 *  ------------------------------------------------------
 *  Este servico guarda o contador em um ConcurrentHashMap, ou seja, na RAM
 *  do processo. Isso e uma escolha CONSCIENTE e possui um tradeoff que
 *  vale explicar em entrevista:
 *
 *  - A VANTAGEM: zero infraestrutura extra. Nao precisa de Redis, nem de
 *    tabela no banco. Funciona com um unico comando e escala bem para o
 *    volume de um sistema interno ou de avaliacao.
 *
 *  - A LIMITACAO: o contador vive na memoria do processo. Se a aplicacao
 *    subir com 3 instancias, cada uma tem seu proprio contador (um
 *    atacante ganha 3x o limite). E se a aplicacao reiniciar, os
 *    contadores zeram.
 *
 *  Em producao real, a solucao canonica e guardar esse estado no REDIS
 *  (com TTL, que e o mesmo tempo da janela) ou em uma tabela no banco.
 *  A interface deste servico ja esta pronta para isso: basta trocar o
 *  "Map" por um "RedisTemplate" - o resto do codigo nao muda.
 *
 *  ------------------------------------------------------
 *  UMA REGRA IMPORTANTE: NAO REVELAR QUEM EXISTE
 *  ------------------------------------------------------
 *  O contador e por (IP + usuario). Isso significa que um atacante pode
 *  descobrir se um usuario existe: se ele errar a senha 6 vezes e o
 *  sistema devolver 429 (bloqueado), o usuario provavelmente existe. Por
 *  isso a mensagem de erro devolvida ao cliente e SEMPRE a mesma
 *  ("credenciais invalidas"), independentemente do motivo real - tanto
 *  no caso de "usuario inexistente" quanto de "senha errada" e de
 *  "conta bloqueada". Ver AuthenticationAuditHandler.
 * ============================================================================
 */
@Service
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Chave usada para agregar (juntar) as tentativas de um mesmo IP, ignorando
     * o usuario. Separada da chave por usuario para nao misturar os dois
     * limites.
     */
    private static final String SEPARADOR = "|";

    /**
     * Contadores de falhas.
     *
     * CHAVE -> ESTRUTURA DE CONTAGEM
     *   "usuario|ip"   -> falhas daquele usuario naquele IP
     *   "ip"           -> falhas totais vindas daquele IP (qualquer usuario)
     *
     * ConcurrentHashMap: thread-safe. Isso e obrigatorio aqui, porque a API
     * atende varias requisicoes ao mesmo tempo (o Tomcat usa um pool de
     * threads) e dois acessos simultaneos ao mesmo HashMap corromperiam
     * a estrutura.
     */
    private final Map<String, RegistroTentativa> tentativas = new ConcurrentHashMap<>();

    /**
     * Liga/desliga o controle de tentativas.
     * Vem de app.security.rate-limit.enabled (padrao: ligado).
     */
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean habilitado;

    /**
     * Maximo de falhas do MESMO USUARIO (no mesmo IP) antes de bloquear.
     * Padrao: 5 tentativas.
     */
    @Value("${app.security.rate-limit.max-attempts:5}")
    private int maxTentativasPorUsuario;

    /**
     * Maximo de falhas vindas do MESMO IP (somando todos os usuarios)
     * antes de bloquear o IP inteiro.
     * Padrao: 20 tentativas.
     */
    @Value("${app.security.rate-limit.max-attempts-per-ip:20}")
    private int maxTentativasPorIp;

    /**
     * Duracao da janela de tempo, em MINUTOS.
     * Depois de 15 minutos sem nova falha, o contador zera sozinho.
     * Padrao: 15 minutos.
     */
    @Value("${app.security.rate-limit.window-minutes:15}")
    private long janelaMinutos;

    /**
     * Registra uma tentativa de autenticacao FALHADA.
     *
     * E chamada pelo AuthenticationAuditHandler sempre que o Spring
     * Security recusa as credenciais.
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
     * Registra uma autenticacao BEM-SUCEDIDA.
     *
     * E chamada pelo AuthenticationSuccessAuditListener sempre que o
     * Spring Security aceitou as credenciais.
     *
     * Ao autenticar com sucesso, o contador de falhas do usuario e
     * zerado. O zeraimento e feito para o usuario INTEIRO (todos os IPs),
     * e nao apenas para o par (usuario, IP). A justificativa e de
     * seguranca: quem provou que sabe a senha nao deve continuar
     * bloqueado por tentativas antigas - inclusive tentativas que
     * vieram de um IP diferente do atual.
     *
     * O contador do IP NAO e zerado, porque ele existe para detectar o
     * volume total de ataques, nao o historico de um usuario especifico.
     *
     * @param usuario nome de usuario autenticado
     */
    public void registrarSucesso(String usuario) {

        if (!habilitado) {
            return;
        }

        // Remove todos os registros (de qualquer IP) que pertencem a este
        // usuario. O filtro usa "removeIf", que e seguro mesmo com
        // acesso concorrente ao mapa.
        String sufixo = SEPARADOR + (usuario == null ? "" : usuario);

        tentativas.entrySet().removeIf(entry ->
                entry.getKey().endsWith(sufixo) && !entry.getKey().startsWith("ip" + SEPARADOR));

        LOG.info("Tentativa de autenticacao BEM-SUCEDIDA. usuario={}", usuario);
    }

    /**
     * Diz se o IP esta bloqueado no momento.
     *
     * Este metodo e chamado ANTES da autenticacao (no
     * BruteForceProtectionFilter). Se o IP ja estourou o limite global,
     * nem vale a pena tentar validar a senha - responder 429 direto economiza
     * CPU (o BCrypt e caro) e encurta o ataque.
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
     * Serve para montar o cabecalho HTTP "Retry-After", que diz ao cliente
     * "tente de novo em N segundos". E uma boa pratica de UX e de seguranca:
     * o cliente sabe o tempo de espera em vez de martelar o servidor.
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
     * Calcula quantos segundos faltam para o bloqueio DESTE USUARIO
     * expirar.
     *
     * Verifica primeiro o registro do par (usuario, IP) e, se nao houver,
     * cai para o registro do IP. A ordem importa: o bloqueio do usuario
     * e o mais especifico e, portanto, o que o cliente precisa esperar.
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
     * liberar todo mundo (por exemplo, apos um alerta falso).
     */
    public void limpar() {
        tentativas.clear();
    }

    /**
     * Incrementa o contador de uma chave e renova a validade da janela.
     */
    private void incrementar(String chave) {

        Instant agora = Instant.now();

        // compute(): calcula e atualiza o contador de forma ATOMICA.
        // Sem isso, duas threads que chegam ao mesmo tempo poderiam
        // sobrescrever o contador uma da outra e perder uma tentativa.
        tentativas.compute(chave, (k, existente) -> {
            if (existente == null || existente.expirada(agora)) {
                // primeira tentativa (ou a janela anterior ja venceu)
                return new RegistroTentativa(1, agora.plus(Duration.ofMinutes(janelaMinutos)));
            }
            existente.contador.incrementAndGet();
            // A cada nova falha, a janela e renovada: e assim que o
            // controle "deslizante" funciona (o atacante tem 5 falhas
            // a cada 15 minutos, nao 5 no total para sempre).
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
     * Remove da memoria os registros ja expirados (limpeza em tempo de
     * acesso, para nao precisar de um agendador).
     */
    private Map<String, RegistroTentativa> registrosAtivos() {

        Instant agora = Instant.now();

        // removeIf: apaga do mapa as entradas vencidas.
        tentativas.entrySet().removeIf(entry -> entry.getValue().expirada(agora));

        return tentativas;
    }

    /**
     * Monta a chave do par (usuario, IP).
     * O IP vem primeiro para deixar a chave legivel no log.
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
     * crescimento da estrutura. Esta tarefa e a "rede de seguranca":
     * garante que a memoria e liberada mesmo que ninguem mais faca
     * requisicao ao sistema (por exemplo, de madrugada, com o servidor
     * ocioso e o contador parado).
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
     * =========================================================================
     *  REGISTRO DE TENTATIVAS (classe interna)
     * =========================================================================
     *
     *  Agrupa o CONTADOR (quantas falhas) e o VALIDADE (ate quando vale).
     *  O contador e um AtomicInteger porque varias threads podem falhar ao
     *  mesmo tempo no mesmo par (usuario, IP).
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
