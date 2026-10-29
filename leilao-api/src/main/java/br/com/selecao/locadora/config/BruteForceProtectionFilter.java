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
 * ============================================================================
 *  FILTRO: BLOQUEIO DE FORCA BRUTA
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  ONDE ESTE FILTRO SE POSICIONA?
 *  ------------------------------------------------------
 *  Este filtro roda ANTES do filtro de autenticacao basica do Spring
 *  Security (BasicAuthenticationFilter). A ordem importa muito:
 *
 *      requisicao
 *          |
 *          v
 *      [BruteForceProtectionFilter]   <-- estamos aqui
 *          |  IP bloqueado?  -> 429, para aqui. Nem tenta validar a senha.
 *          v
 *      [BasicAuthenticationFilter]    -- valida usuario e senha (BCrypt)
 *          |  falhou? -> AuthenticationAuditHandler registra a falha
 *          v
 *      [controller]                   -- so executa se autenticou (em producao)
 *
 *  Se o filtro de bloqueio viesse DEPOIS, o atacante continuaria gastando
 *  CPU com o BCrypt (que e lento de proposito) mesmo com a conta
 *  bloqueada - ou seja, a defesa existiria mas nao serviria para nada.
 *
 *  ------------------------------------------------------
 *  POR QUE "OncePerRequestFilter"?
 *  ------------------------------------------------------
 *  Essa classe base do Spring garante que o filtro execute UMA VEZ por
 *  requisicao. Em aplicacoes com filtros encadeados (dispatcher servlet,
 *  filtros de erro do container), o mesmo filtro pode ser chamado mais de
 *  uma vez para a mesma requisicao - e o contador de tentativas seria
 *  inflado sem motivo.
 *
 *  ------------------------------------------------------
 *  O QUE ACONTECE QUANDO O IP ESTA BLOQUEADO?
 *  ------------------------------------------------------
 *  A API responde:
 *      HTTP 429 Too Many Requests
 *      + cabecalho "Retry-After" com o numero de segundos ate liberar
 *      + corpo JSON padronizado
 *
 *  O status 429 e o codigo oficial do HTTP para "excesso de requisicoes".
 *  Diferente do 403 ("proibido"), ele informa que a situacao e TEMPORARIA
 *  e o cliente pode tentar de novo depois - que e exatamente a verdade.
 *
 *  IMPORTANTE: a resposta NAO diz qual era o usuario tentado nem quantas
 *  falhas faltam. Devolver esse detalhe ajudaria o atacante a medir o
 *  proprio ataque.
 * ============================================================================
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
