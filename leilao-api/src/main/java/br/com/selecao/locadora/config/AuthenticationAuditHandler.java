package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Registra as tentativas de autenticacao.
 *
 * A classe nao escreve a resposta HTTP: ela so registra o evento. Quem
 * responde e o filtro de autenticacao, assim nao existem dois lugares
 * escrevendo na mesma resposta.
 *
 * Sucesso e falha sao registrados no mesmo formato, e a falha nunca revela
 * se o usuario existe: "credenciais invalidas" nos dois casos.
 */
@Component
public class AuthenticationAuditHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationAuditHandler.class);

    /**
     * Atributo da requisicao onde guardamos o resultado da tentativa de
     * autenticacao.
     *
     * Fica no request porque o resultado precisa viajar do filtro de
     * autenticacao (que detectou a falha) ate o ponto de entrada de
     * autenticacao (que escreve a resposta). A requisicao e o unico
     * objeto que os dois tem em comum.
     */
    public static final String ATRIBUTO_RESULTADO = "resultadoAutenticacao";

    /**
     * Resultado: as credenciais estavam erradas. A resposta sera 401.
     */
    public static final String RESULTADO_INVALIDAS = "CREDENCIAIS_INVALIDAS";

    /**
     * Resultado: a conta esta bloqueada por excesso de tentativas.
     * A resposta sera 429.
     */
    public static final String RESULTADO_BLOQUEADO = "CONTA_BLOQUEADA";

    /**
     * Atributo da requisicao onde guardamos quantos segundos o cliente
     * precisa esperar antes de tentar de novo (vira o cabecalho HTTP
     * "Retry-After").
     */
    public static final String ATRIBUTO_SEGUNDOS = "segundosAteLiberar";

    /** Servico que mantem a contagem de tentativas de login. */
    @Autowired
    private LoginAttemptService loginAttemptService;

    /**
     * Indica se a aplicacao esta atras de um proxy reverso confiavel.
     */
    @Value("${app.security.trust-proxy:false}")
    private boolean confiarEmProxy;

    /**
     * Registra uma tentativa de autenticacao que deu errado e decide o que
     * a requisicao deve receber.
     *
     * Este metodo nao escreve na resposta, apenas marca a requisicao e
     * registra o evento no log. Quem escreve a resposta e o
     * JsonAuthenticationEntryPoint.
     *
     * @param request   requisicao HTTP em andamento
     * @param exception a excecao de autenticacao (motivo da recusa)
     */
    public void registrarFalha(HttpServletRequest request, AuthenticationException exception) {

        String ip = ClientIpResolver.resolver(request, confiarEmProxy);
        String usuario = extrairUsuario(request);

        // A conta ja estava bloqueada por tentativas anteriores?
        //
        // A verificacao vem antes de registrar esta nova falha, pra que o
        // usuario receba exatamente o numero de tentativas que o limite
        // permite. Com limite 5:
        //
        //   tentativa 1 -> 401 (falha 1 registrada)
        //   tentativa 2 -> 401 (falha 2 registrada)
        //   ...
        //   tentativa 5 -> 401 (falha 5 registrada)
        //   tentativa 6 -> 429 (conta bloqueada)
        //
        // Se a verificacao viesse depois, a quinta tentativa ja responderia
        // 429 - o usuario teria so 4 palpites uteis, e nao 5.
        if (loginAttemptService.usuarioBloqueado(ip, usuario)
                || loginAttemptService.ipBloqueado(ip)) {

            long segundos = loginAttemptService.segundosAteLiberar(ip, usuario);

            LOG.warn("AUDITORIA | LOGIN_BLOQUEADO | usuario={} ip={} segundos_ate_liberar={}",
                    usuario, ip, segundos);

            // Header que informa ao cliente quando pode tentar de novo.
            request.setAttribute(ATRIBUTO_SEGUNDOS, segundos);

            // Marca a requisicao: a resposta sera 429.
            request.setAttribute(ATRIBUTO_RESULTADO, RESULTADO_BLOQUEADO);

            return;
        }

        // Registra a falha no controle de tentativas.
        loginAttemptService.registrarFalha(ip, usuario);

        LOG.warn("AUDITORIA | LOGIN_FALHOU | usuario={} ip={} motivo={}",
                usuario, ip, exception.getClass().getSimpleName());

        // Marca a requisicao: a resposta sera 401.
        request.setAttribute(ATRIBUTO_RESULTADO, RESULTADO_INVALIDAS);
    }

    /**
     * Extrai o nome de usuario da requisicao.
     *
     * No HTTP Basic, o navegador manda o cabecalho
     * "Authorization: Basic base64(usuario:senha)". O base64 e so uma
     * codificacao, nao criptografia, mas ele evita que a senha apareca
     * "em claro" no cabecalho - o que faria ela aparecer em log de proxy,
     * em historico de rede e em ferramentas de captura de trafego.
     *
     * @param request requisicao HTTP
     * @return o usuario informado, ou "desconhecido" se nao der pra extrair
     */
    private String extrairUsuario(HttpServletRequest request) {

        String cabecalho = request.getHeader("Authorization");

        if (cabecalho == null || !cabecalho.startsWith("Basic ")) {
            return "desconhecido";
        }

        try {
            // Decodifica o base64 e separa usuario de senha na primeira
            // ":". A senha em si e descartada: nao precisamos dela, e
            // guarda-la em variavel seria o contrario da boa pratica.
            String credencial = new String(java.util.Base64.getDecoder()
                    .decode(cabecalho.substring(6).trim()), java.nio.charset.StandardCharsets.UTF_8);

            int separador = credencial.indexOf(':');

            if (separador > 0) {
                return credencial.substring(0, separador);
            }
        } catch (IllegalArgumentException e) {
            // Base64 invalido: o cliente nao esta usando HTTP Basic valido.
            LOG.debug("Cabecalho Authorization mal formado; usuario nao identificavel.");
        }

        return "desconhecido";
    }
}
