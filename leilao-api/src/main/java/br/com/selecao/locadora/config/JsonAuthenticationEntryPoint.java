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
 * Unico escritor da resposta "acesso negado".
 *
 * O padrao do Spring devolve uma pagina HTML, que quebraria o frontend.
 * Esta classe devolve o mesmo JSON do GlobalExceptionHandler.
 *
 * A resposta nao revela se o recurso existe nem se o usuario existe: o
 * detalhe fica no log do servidor.
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
     * Mensagem unica devolvida em qualquer falha de autenticacao.
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
            // O valor e um "long", que o Java empacota como "Long" (e nao
            // como "Integer"). Por isso a conversao e feita via "Number",
            // que aceita os dois tipos - se fizessemos "(Integer)
            // atributo", teriamos um ClassCastException e o cabecalho
            // "Retry-After" sairia sempre zerado.
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

        // Mensagem generica e identica para qualquer motivo de recusa.
        response.getWriter().write(
                "{\"status\":401,"
                        + "\"erro\":\"Nao autorizado\","
                        + "\"mensagem\":\"" + MENSAGEM_CREDENCIAIS_INVALIDAS + "\"}");
    }
}
