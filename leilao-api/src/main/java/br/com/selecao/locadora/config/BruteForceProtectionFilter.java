package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Bloqueio por forca bruta.
 *
 * Roda antes do filtro de autenticacao basica, para que um IP ja bloqueado
 * nem chegue a comparar a senha - o BCrypt e lento de proposito, e nao
 * adianta gastar esse tempo.
 *
 * Um IP bloqueado recebe 429, com o cabecalho Retry-After.
 */
@Component
public class BruteForceProtectionFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(BruteForceProtectionFilter.class);

    /** Servico que mantem a contagem de tentativas. */
    @Autowired
    private LoginAttemptService loginAttemptService;

    /**
     * Indica se a aplicacao esta atras de um proxy reverso confiavel.
     * Quando true, o IP e lido do cabecalho X-Forwarded-For.
     */
    @Value("${app.security.trust-proxy:false}")
    private boolean confiarEmProxy;

    /**
     * Caminhos que NUNCA sao bloqueados pelo controle de forca bruta.
     *
     * O motivo e OPERACIONAL e vale explicar: o endpoint
     * "/actuator/health" e o que o orquestrador (Docker, Kubernetes)
     * consulta para saber se a aplicacao esta viva. Se ele devolvesse
     * HTTP 429 durante um ataque de forca bruta, o orquestrador
     * interpretaria "a aplicacao esta doente" e REINICIARIA o
     * container - o ataque de senha viraria um ataque de negacao de
     * servico.
     *
     * O mesmo vale para as demais rotas de monitoracao: elas precisam
     * funcionar sempre, mesmo sob ataque.
     */
    private static final String[] CAMINHOS_ISENTOS = {
            "/actuator/health",
            "/actuator/info",
            "/error"
    };

    /**
     * Executa a verificacao de bloqueio ANTES da autenticacao.
     *
     * @param request     requisicao HTTP
     * @param response    resposta HTTP
     * @param chain       cadeia de filtros seguinte
     * @throws ServletException se occurer erro no processamento
     * @throws IOException      se occurer erro de leitura/escrita
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Caminhos de monitoracao passam livre, mesmo sob ataque.
        if (isCaminhoIsento(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // Descobre o IP real de quem fez a requisicao.
        String ip = ClientIpResolver.resolver(request, confiarEmProxy);

        // Consulta o controle de tentativas.
        if (loginAttemptService.ipBloqueado(ip)) {

            long segundos = loginAttemptService.segundosAteLiberar(ip);

            LOG.warn("Requisicao BLOQUEADA por controle de forca bruta. ip={} segundos_ate_liberar={}", ip, segundos);

            // Cabecalho que informa ao cliente quando pode tentar de novo.
            response.setHeader("Retry-After", String.valueOf(segundos));

            // Resposta padronizada (mesmo formato do GlobalExceptionHandler).
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            response.getWriter().write(
                    "{\"status\":429,"
                            + "\"erro\":\"Muitas tentativas de acesso\","
                            + "\"mensagem\":\"Numero excessivo de tentativas de autenticacao. "
                            + "Aguarde alguns minutos antes de tentar novamente.\"}");

            // IMPORTANTE: NAO chama chain.doFilter(...). A requisicao morre
            // aqui - o controller nunca e acionado, a senha nunca e validada.
            return;
        }

        // Nada bloqueado: segue a cadeia normal de filtros.
        chain.doFilter(request, response);
    }

    /**
     * Diz se o caminho da requisicao e um dos caminhos isentos.
     *
     * @param caminho caminho da requisicao (sem query string)
     * @return true se o caminho deve passar sem verificacao de bloqueio
     */
    private boolean isCaminhoIsento(String caminho) {

        if (caminho == null) {
            return false;
        }

        for ( String isento : CAMINHOS_ISENTOS ) {
            if ( caminho.startsWith( isento ) ) {
                return true;
            }
        }

        return false;
    }
}
