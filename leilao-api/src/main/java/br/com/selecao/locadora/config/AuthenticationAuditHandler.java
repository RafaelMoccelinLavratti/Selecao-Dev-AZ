package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * ============================================================================
 *  SERVICO: AUDITORIA DAS TENTATIVAS DE AUTENTICACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  PARA QUE SERVE?
 *  ------------------------------------------------------
 *  Uma API sem registro de tentativas de autenticacao e uma API cega. O
 *  requisito "registrar as tentativas de acesso" aparece em quase toda
 *  politica de seguranca da informacao (ISO 27001, controle A.9, LGPD para
 *  trilha de auditoria, NIST 800-53 AU-2).
 *
 *  Esta classe registra o que aconteceu a cada tentativa de autenticacao e
 *  decide se a requisicao deve ser recusada com 401 (credenciais invalidas)
 *  ou com 429 (conta bloqueada por forca bruta).
 *
 *  ------------------------------------------------------
 *  POR QUE ESTA CLASSE NAO ESCREVE A RESPOSTA HTTP?
 *  ------------------------------------------------------
 *  Este e o ponto mais importante do arquivo, e um detalhe que custou
 *  horas de depuracao - e que vale MUITO a pena explicar em entrevista.
 *
 *  No Spring Security 5.7, quando o BasicAuthenticationFilter recusa as
 *  credenciais, ele NAO interrompe a cadeia de filtros: o metodo
 *  "onUnsuccessfulAuthentication" e chamado e a requisicao CONTINUA
 *  andando. Mais adiante, o FilterSecurityInterceptor percebe que nao ha
 *  usuario autenticado e recusa o acesso; o ExceptionTranslationFilter
 *  entao chama o "AuthenticationEntryPoint", que ESCREVE a resposta
 *  "acesso negado".
 *
 *  Ou seja: se esta classe tambem escrevesse a resposta, teriamos DOIS
 *  escritores na mesma resposta:
 *
 *      BasicAuthenticationFilter  ->  escreve 429
 *              |
 *              v  (a cadeia continua)
 *      FilterSecurityInterceptor  ->  AccessDeniedException
 *              |
 *              v
 *      ExceptionTranslationFilter ->  chama o EntryPoint
 *              |
 *              v
 *      AuthenticationEntryPoint   ->  escreve 401 POR CIMA do 429
 *
 *  Resultado: o cliente receberia um corpo com OS DOIS JSONs colados
 *  ("{...429...}{...401...}") e o status 401 - o 429 se perderia
 *  completamente. E o cliente que lesse o primeiro objeto receberia a
 *  informacao errada.
 *
 *  A SOLUCAO (e o padrao correto):
 *  Quem escreve a resposta de "acesso negado" e UM SO - o
 *  AuthenticationEntryPoint. Esta classe apenas:
 *    1) registra a tentativa no log de auditoria;
 *    2) conta a falha no controle anti-forca-bruta;
 *    3) MARCA a requisicao com o resultado, em um atributo da request.
 *
 *  O EntryPoint le esse atributo e decide se responde 401 ou 429. Assim
 *  existe um unico escritor de resposta, e nenhum corpo duplicado.
 *
 *  ------------------------------------------------------
 *  O QUE E GRAVADO NO LOG?
 *  ------------------------------------------------------
 *  | Evento        | Nivel | Informacao                          |
 *  |---------------|-------|-------------------------------------|
 *  | LOGIN_FALHOU  | WARN  | usuario, ip, motivo                 |
 *  | LOGIN_BLOQ.   | WARN  | usuario, ip, segundos ate liberar    |
 *
 *  O QUE NAO E GRAVADO (e por que):
 *  - A SENHA nunca. Nem em claro, nem hasheada. O log e um texto simples
 *    que qualquer pessoa com acesso ao servidor le. Se a senha entrasse
 *    nele, o log viraria um novo ponto de vazamento de credenciais.
 *  - O TOKEN de autenticacao nunca.
 *
 *  ------------------------------------------------------
 *  REGRA DE OURO: NAO REVELAR QUEM EXISTE
 *  ------------------------------------------------------
 *  Se a API devolvesse mensagens diferentes, um atacante conseguiria
 *  ENUMERAR USUARIOS:
 *
 *      "usuario nao encontrado"   -> o usuario existe? NAO
 *      "senha incorreta"         -> o usuario existe? SIM
 *
 *  Com essa informacao, o atacante monta uma lista de usernames validos
 *  e so precisa adivinhar as senhas. E o que o OWASP chama de "User
 *  Enumeration" (item A07:2021).
 *
 *  A SOLUCAO: a mensagem devolvida ao cliente e SEMPRE a mesma
 *  ("credenciais invalidas"), independente do motivo real. Os detalhes
 *  continuam no log do servidor, onde so a TI chega.
 * ============================================================================
 */
@Component
public class AuthenticationAuditHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationAuditHandler.class);

    /**
     * Nome do atributo da requisicao onde guardamos o resultado da
     * tentativa de autenticacao.
     *
     * E um atributo da request porque o resultado precisa viajar do filtro
     * de autenticacao (que detectou a falha) ate o ponto de entrada de
     * autenticacao (que escreve a resposta). A requisicao e o unico
     * objeto que eles tem em comum.
     */
    public static final String ATRIBUTO_RESULTADO = "resultadoAutenticacao";

    /**
     * Valor do atributo: as credenciais estavam erradas.
     * A resposta sera 401.
     */
    public static final String RESULTADO_INVALIDAS = "CREDENCIAIS_INVALIDAS";

    /**
     * Valor do atributo: a conta esta bloqueada por excesso de
     * tentativas. A resposta sera 429.
     */
    public static final String RESULTADO_BLOQUEADO = "CONTA_BLOQUEADA";

    /**
     * Nome do atributo da requisicao onde guardamos quantos segundos o
     * cliente precisa esperar antes de tentar de novo (vira o cabecalho
     * HTTP "Retry-After").
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
     * Registra uma tentativa de autenticacao FALHADA e decide o que a
     * requisicao deve receber.
     *
     * Este metodo NAO escreve na resposta - apenas marca a requisicao
     * e registra o evento no log. Quem escreve a resposta e o
     * JsonAuthenticationEntryPoint.
     *
     * @param request   requisicao HTTP em andamento
     * @param exception a excecao de autenticacao (motivo da recusa)
     */
    public void registrarFalha(HttpServletRequest request, AuthenticationException exception) {

        String ip = ClientIpResolver.resolver(request, confiarEmProxy);
        String usuario = extrairUsuario(request);

        // ---------------------------------------------------------------
        //  A conta JA estava bloqueada por tentativas ANTERIORES?
        // ---------------------------------------------------------------
        //  A verificacao vem ANTES de registrar esta nova falha, para que
        //  o usuario receba exatamente o numero de tentativas que o
        //  limite permite. Exemplo com limite 5:
        //
        //      tentativa 1 -> 401 (falha 1 registrada)
        //      tentativa 2 -> 401 (falha 2 registrada)
        //      ...
        //      tentativa 5 -> 401 (falha 5 registrada)
        //      tentativa 6 -> 429 (conta bloqueada)
        //
        //  Se a verificacao viesse DEPOIS de registrar a falha, a quinta
        //  tentativa ja responderia 429 - ou seja, o usuario teria
        //  apenas 4 palpites utilizaveis, e nao 5.
        // ---------------------------------------------------------------
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
     * No HTTP Basic, o navegador envia o cabecalho
     * "Authorization: Basic base64(usuario:senha)". O base64 e apenas uma
     * codificacao (NAO e criptografia), mas ele impede que a senha
     * apareca "em claro" no cabecalho - o que faria com que ela aparecesse
     * em log de proxy, em historico de rede e em ferramentas de captura
     * de trafego.
     *
     * @param request requisicao HTTP
     * @return o usuario informado, ou "desconhecido" se nao for possivel
     *         extrair
     */
    private String extrairUsuario(HttpServletRequest request) {

        String cabecalho = request.getHeader("Authorization");

        if (cabecalho == null || !cabecalho.startsWith("Basic ")) {
            return "desconhecido";
        }

        try {
            // Decodifica o base64 e separa usuario de senha na PRIMEIRA
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
