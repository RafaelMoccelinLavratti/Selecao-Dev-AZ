package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Controla as tentativas de login.
 *
 * O bloqueio existe em dois niveis: por usuario e por IP, porque sao dois
 * ataques diferentes - um mira numa conta especifica, o outro mira no
 * sistema inteiro.
 *
 * Onde os contadores ficam depende de quantas replicas a API tem. Na
 * memoria (o padrao), o estado se perde no restart e vale so para a
 * instancia. No banco, o limite vale para a API inteira - que e o que
 * importa quando ha varias replicas atras do balanceador, porque senao o
 * atacante distribuiria as tentativas entre elas.
 *
 * A escolha e' feita em app.security.rate-limit.store.
 */
@Service
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Prefixo das chaves de tentativa de login.
     *
     * O prefixo separa o contador de tentativas do contador de
     * requisicoes: os dois Divide a mesma estrutura, e um nao pode
     * interferir no outro.
     */
    private static final String PREFIXO = "tentativa:";

    /**
     * Prefixo do contador de um usuario (somando todos os IPs).
     */
    private static final String PREFIXO_USUARIO = PREFIXO + "usuario:";

    /**
     * Prefixo do contador de um IP (somando todos os usuarios).
     */
    private static final String PREFIXO_IP = PREFIXO + "ip:";

    /**
     * Onde os contadores ficam guardados.
     */
    @Autowired
    private ContadorStore contadorStore;

    /**
     * Indica se o controle anti-forca-bruta esta ativo.
     */
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean habilitado;

    /**
     * Maximo de tentativas do mesmo usuario, vindas do mesmo IP, dentro
     * da janela.
     */
    @Value("${app.security.rate-limit.max-attempts:5}")
    private int maxTentativasPorUsuario;

    /**
     * Maximo de tentativas vindas do mesmo IP, somando todos os
     * usuarios, dentro da janela.
     */
    @Value("${app.security.rate-limit.max-attempts-per-ip:20}")
    private int maxTentativasPorIp;

    /**
     * Tamanho da janela, em minutos.
     */
    @Value("${app.security.rate-limit.window-minutes:15}")
    private long janelaMinutos;

    /**
     * Registra uma tentativa que falhou.
     *
     * Sobe dois contadores: o do par (usuario, IP) e o do IP. O primeiro
     * protege uma conta especifica; o segundo protege o sistema contra
     * o ataque que varre varias contas de uma vez.
     *
     * @param ip      endereco IP de origem da requisicao
     * @param usuario nome de usuario informado na tentativa
     */
    public void registrarFalha(String ip, String usuario) {

        if (!habilitado) {
            return;
        }

        // Incrementa o contador do par (usuario, IP).
        contadorStore.incrementar(chaveUsuario(ip, usuario), Duration.ofMinutes(janelaMinutos));

        // Incrementa o contador do IP (somando todos os usuarios).
        contadorStore.incrementar(chaveIp(ip), Duration.ofMinutes(janelaMinutos));

        LOG.warn("Tentativa de autenticacao FALHOU. usuario={} ip={} "
                        + "falhas_usuario={} falhas_ip={}",
                usuario, ip, contar(chaveUsuario(ip, usuario)), contar(chaveIp(ip)));
    }

    /**
     * Registra uma autenticacao que deu certo.
     *
     * Um login bem-sucedido zera o contador de falhas do usuario. O
     * zeraimento vale pro usuario inteiro, de qualquer IP: quem acabou de
     * provar que sabe a senha nao precisa ficar preso por tentativas
     * antigas, nem por tentativas vindas de outro lugar.
     *
     * O contador do IP nao e zerado, porque ele existe pra medir o
     * volume total de ataques, e nao o historico de um usuario.
     *
     * @param usuario nome de usuario autenticado
     */
    public void registrarSucesso(String usuario) {

        if (!habilitado) {
            return;
        }

        // Tira todos os contadores que pertencem a este usuario, de
        // qualquer IP.
        int removidos = contadorStore.removerPorPrefixo(PREFIXO_USUARIO + usuario + ":");

        LOG.info("Tentativa de autenticacao BEM-SUCEDIDA. usuario={} contadores_zerados={}",
                usuario, removidos);
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

        return contar(chaveIp(ip)) >= maxTentativasPorIp;
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

        return contar(chaveUsuario(ip, usuario)) >= maxTentativasPorUsuario;
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

        return segundosRestantes(contadorStore.consultar(chaveIp(ip)));
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

        Contagem doUsuario = contadorStore.consultar(chaveUsuario(ip, usuario));

        if (doUsuario.getQuantidade() > 0) {
            return segundosRestantes(doUsuario);
        }

        return segundosRestantes(contadorStore.consultar(chaveIp(ip)));
    }

    /**
     * Zera todos os contadores.
     * Usado nos testes e como ponto de partida caso a aplicacao precise
     * liberar todo mundo (por exemplo, depois de um alarme falso).
     */
    public void limpar() {
        contadorStore.limpar();
    }

    /**
     * Diz quantas falhas o usuario acumulou neste IP.
     *
     * Existe para o diagnostico e para os testes: e a forma de conferir
     * que os dois caminhos de login (HTTP Basic e JSON) somam no mesmo
     * contador. Sem este metodo, nao daria para provar de dentro que o
     * login por JSON tambem esta sendo limitado.
     *
     * @param ip      endereco IP de origem
     * @param usuario nome de usuario
     * @return o numero de falhas registradas na janela
     */
    public int contarTentativas(String ip, String usuario) {
        return contar(chaveUsuario(ip, usuario));
    }

    /**
     * Diz quantas falhas o IP acumulou, somando todas as tentativas
     * feitas a partir dele (de qualquer usuario).
     *
     * @param ip endereco IP de origem
     * @return o numero de falhas registradas na janela
     */
    public int contarTentativas(String ip) {
        return contar(chaveIp(ip));
    }

    /**
     * Le a quantidade de falhas de uma chave (0 se nao houver registro).
     */
    private int contar(String chave) {
        return contadorStore.consultar(chave).getQuantidade();
    }

    /**
     * Converte o prazo de uma contagem em segundos restantes.
     */
    private long segundosRestantes(Contagem contagem) {

        if (contagem.getQuantidade() == 0) {
            return 0;
        }

        long restante = Duration.between(Instant.now(), contagem.getExpiraEm()).getSeconds();

        return Math.max(restante, 0);
    }

    /**
     * A chave do contador do par (usuario, IP).
     *
     * O usuario vem primeiro de proposito: assim, o contador de um
     * usuario pode ser encontrado pelo prefixo, e zeres em todos os IPs
     * de uma vez.
     */
    private String chaveUsuario(String ip, String usuario) {
        return PREFIXO_USUARIO + (usuario == null ? "" : usuario) + ":ip:" + ip;
    }

    /**
     * A chave do contador do IP (somando todos os usuarios).
     */
    private String chaveIp(String ip) {
        return PREFIXO_IP + ip;
    }

    /**
     * Remove os contadores cuja janela ja venceu.
     *
     * Roda de tempos em tempos, para a estrutura nao crescer sem
     * limite. Sem isso, cada IP que tentasse um login deixaria um
     * registro para sempre.
     */
    @Scheduled(fixedDelayString = "${app.security.rate-limit.cleanup-interval-ms:60000}",
            initialDelayString = "${app.security.rate-limit.cleanup-interval-ms:60000}")
    public void limparExpirados() {
        int removidos = contadorStore.limparExpirados();

        if (removidos > 0) {
            LOG.debug("Contadores de tentativa expirados removidos: {}", removidos);
        }
    }
}
