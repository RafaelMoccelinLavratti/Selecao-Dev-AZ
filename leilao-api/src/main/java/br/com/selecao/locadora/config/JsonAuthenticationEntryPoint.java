package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * ============================================================================
 *  PONTO DE ENTRADA DE AUTENTICACAO (UNICO ESCRITOR DA RESPOSTA NEGADA)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE ACONTECE SEM ESTA CLASSE?
 *  ------------------------------------------------------
 *  Quando o Spring Security bloqueia uma requisicao, ele chama o
 *  "AuthenticationEntryPoint". O padrao do Spring devolve uma pagina HTML
 *  com o formulario de login basico do navegador.
 *
 *  Numa API REST isso e um problema: o frontend espera JSON, e receber
 *  um HTML quebra o parser do axios e transforma um erro de autenticacao
 *  em um erro generico de "falha de rede" na tela do usuario - que nao
 *  ajuda ninguem a entender o que aconteceu.
 *
 *  ------------------------------------------------------
 *  POR QUE ESTA CLASSE E O UNICO QUE ESCREVE A RESPOSTA
 *  ------------------------------------------------------
 *  Este ponto e o coracao do desenho de seguranca do projeto, e vale
 *  explicar com calma em entrevista.
 *
 *  No Spring Security 5.7, quando o BasicAuthenticationFilter recusa as
 *  credenciais, a requisicao NAO para ali: ela CONTINUA pela cadeia de
 *  filtros. Mais adiante, o FilterSecurityInterceptor recusa o acesso
 *  (AccessDeniedException) e o ExceptionTranslationFilter chama ESTE
 *  metodo, que escreve a resposta.
 *
 *  Portanto, se o handler de falha de autenticacao TAMBEM escrevesse a
 *  resposta, teriamos dois escritores na mesma resposta:
 *
 *      BasicAuthenticationFilter   ->  escreve 429 (conta bloqueada)
 *              |
 *              v  (a cadeia continua)
 *      FilterSecurityInterceptor   ->  AccessDeniedException
 *              |
 *              v
 *      ExceptionTranslationFilter  ->  chama ESTE metodo
 *              |
 *              v
 *      AuthenticationEntryPoint    ->  escreve 401 POR CIMA do 429
 *
 *  O cliente receberia um corpo com OS DOIS JSONs colados
 *  ("{...429...}{...401...}") e o status 401. O 429 - que era a
 *  informacao mais importante para o usuario - se perderia.
 *
 *  A SOLUCAO (que e o que este projeto faz):
 *  - O AuthenticationAuditHandler registra a falha e MARCA a requisicao
 *    com o resultado (credenciais invalidas ou conta bloqueada);
 *  - ESTE metodo le essa marca e escreve a resposta correspondente
 *    (401 ou 429).
 *
 *  Assim existe exatamente UM escritor de resposta, e o corpo nunca vem
 *  duplicado.
 *
 *  ------------------------------------------------------
 *  QUANDO CADA UM DOS COMPORTAMENTOS ACONTECE?
 *  ------------------------------------------------------
 *  | Situacao                                  | Quem responde | Status |
 *  |-------------------------------------------|---------------|--------|
 *  | Sem cabecalho Authorization em rota        | Este metodo   | 401    |
 *  |   protegida                                |               |        |
 *  | Credenciais invalidas                      | Este metodo   | 401    |
 *  |   (marcado pelo handler de auditoria)      |               |        |
 *  | Muitas tentativas seguidas (conta bloqueada)| Este metodo  | 429    |
 *  |   (marcado pelo handler de auditoria)      |               |        |
 *  | IP bloqueado, ANTES mesmo de tentar        | BruteForce    | 429    |
 *  |   autenticar (BruteForceProtectionFilter) | ProtectionFilt|        |
 *
 *  Repare que o 401 de "sem credencial" NAO passa pelo
 *  AuthenticationAuditHandler: ele acontece ANTES da tentativa de
 *  autenticacao (nao ha usuario e senha para conferir). Por isso ele
 *  nao conta como "tentativa falha" e nao bloqueia ninguem. Isso e
 *  intencional - senao um usuario que apenas abre a tela de login
 *  acabaria bloqueando a propria conta.
 *
 *  ------------------------------------------------------
 *  INFORMAVAO SENSIVEL
 *  ------------------------------------------------------
 *  A resposta NAO revela se o recurso existe, nem se o usuario existe.
 *  Ela diz apenas "credenciais invalidas". O detalhe fica no log do
 *  servidor.
 * ============================================================================
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOG = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    /**
     * Indica se a aplicacao esta atras de um proxy reverso confiavel.
     */
    @Value("${app.security.trust-proxy:false}")
    private boolean confiarEmProxy;

    /**
     * Mensagem UNICA devolvida em qualquer falha de autenticacao.
     * Nunca muda - ver a explicacao sobre enumeracao de usuarios na
     * classe AuthenticationAuditHandler.
     */
    private static final String MENSAGEM_CREDENCIAIS_INVALIDAS = "Credenciais invalidas.";

    /**
     * Chamado pelo Spring Security quando a requisicao nao passou pela
     * autenticacao (ou foi recusada).
     *
     * @param request       requisicao HTTP
     * @param response      resposta HTTP
     * @param authException a excecao que motivou o bloqueio
     * @throws IOException se occurer erro de escrita na resposta
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String ip = ClientIpResolver.resolver(request, confiarEmProxy);

        // Le o resultado marcado pelo handler de auditoria durante a
        // tentativa de autenticacao. Se o atributo nao existir, significa
        // que a requisicao nem chegou a tentar autenticar (o cliente nem
        // enviou o cabecalho Authorization) - nesse caso, o padrao e 401.
        String resultado = (String) request.getAttribute(AuthenticationAuditHandler.ATRIBUTO_RESULTADO);

        boolean contaBloqueada = AuthenticationAuditHandler.RESULTADO_BLOQUEADO.equals(resultado);

        if (contaBloqueada) {

            // Tempo de espera informado pelo handler de auditoria.
            // ATENCAO: o valor e um "long", que o Java empacota como
            // "Long" (e nao como "Integer"). Por isso a conversao e feita
            // via "Number", que aceita os dois tipos - se fizessemos
            // "(Integer) atributo", teriamos um ClassCastException e o
            // cabecalho "Retry-After" sairia sempre zerado.
            Object segundos = request.getAttribute(AuthenticationAuditHandler.ATRIBUTO_SEGUNDOS);
            long tempoDeEspera = (segundos instanceof Number) ? ((Number) segundos).longValue() : 0L;

            LOG.warn("AUDITORIA | ACESSO_NEGADO | ip={} caminho={} motivo=CONTA_BLOQUEADA",
                    ip, request.getRequestURI());

            // Cabecalho que informa ao cliente quando pode tentar de novo.
            response.setHeader("Retry-After", String.valueOf(tempoDeEspera));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            response.getWriter().write(
                    "{\"status\":429,"
                            + "\"erro\":\"Muitas tentativas de acesso\","
                            + "\"mensagem\":\"Numero excessivo de tentativas de autenticacao. "
                            + "Aguarde alguns minutos antes de tentar novamente.\"}");

            return;
        }

        // Log do evento. O motivo da excecao fica no log, nao na resposta.
        LOG.warn("AUDITORIA | ACESSO_NEGADO | ip={} caminho={} motivo={}",
                ip, request.getRequestURI(), authException.getClass().getSimpleName());

        // Cabecalho que faz o navegador oferecer o login basico.
        response.setHeader("WWW-Authenticate", "Basic realm=\"Sistema de Leiloes\"");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Mensagem GENERICA e IDENTICA para qualquer motivo de recusa.
        response.getWriter().write(
                "{\"status\":401,"
                        + "\"erro\":\"Nao autorizado\","
                        + "\"mensagem\":\"" + MENSAGEM_CREDENCIAIS_INVALIDAS + "\"}");
    }
}
