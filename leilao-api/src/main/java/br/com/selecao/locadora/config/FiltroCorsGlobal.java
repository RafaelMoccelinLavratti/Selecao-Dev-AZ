package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Filtro de CORS que roda antes de todos os outros filtros.
 *
 * POR QUE ISTO EXISTE
 *
 * O CORS do Spring MVC (o "addCorsMappings" do CorsConfig) so vale
 * para as requisicoes que chegam no DispatcherServlet. Qualquer
 * filtro que responda antes - e o filtro de rate limit responde
 * 429 - devolve a resposta sem os cabecalhos de CORS.
 *
 * O efeito no navegador e' pior do que parece: quando o CORS nao
 * autoriza, o navegador NAO entrega o status nem o corpo ao
 * JavaScript. Ele reporta "erro de CORS" na console. O developer ve
 * um erro de rede onde, na verdade, houve um 429 (muitas requisicoes)
 * - e a aplicacao, no seu interceptor, trata como "falha de rede" e
 * tenta renovar o token, o que dispara MAIS requisicoes.
 *
 * Pior ainda: o preflight (OPTIONS) e' barrado pelo mesmo filtro. E
 * como toda requisicao com o cabecalho "Authorization" gera um
 * preflight, cada chamada do frontend gastaria DUAS vagas do limite
 * de taxa: uma no OPTIONS, outra no GET. O limite efetivo seria a
 * metade do configurado, sem ninguem pedir.
 *
 * O QUE ESTE FILTRO FAZ
 *
 *   1. Responde o preflight (OPTIONS) com 200 e os cabecalhos de CORS.
 *      O preflight nao consome vaga do limite de taxa.
 *
 *   2. Devolve os cabecalhos de CORS em TODA resposta - inclusive nas
 *      barradas por limite, por forca bruta ou por erro. O navegador
 *      passa a ler o status real (429, 403, 401) em vez de reportar
 *      "erro de CORS".
 *
 * A lista de origens e' a mesma do CorsConfig, e nunca "*": com
 * credenciais, "*" permitiria CSRF entre origens.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorsGlobal extends OncePerRequestFilter {

    /**
     * Cabecalho de resposta com a origem autorizada.
     */
    private static final String CABECALHO_ORIGEM = "Access-Control-Allow-Origin";

    /**
     * Cabecalho de resposta com os metodos permitidos.
     */
    private static final String CABECALHO_METODOS = "Access-Control-Allow-Methods";

    /**
     * Cabecalho de resposta com os cabecalhos de requisicao permitidos.
     */
    private static final String CABECALHO_CABECALHOS = "Access-Control-Allow-Headers";

    /**
     * Cabecalho de resposta que libera o envio de credenciais.
     */
    private static final String CABECALHO_CREDENCIAIS = "Access-Control-Allow-Credentials";

    /**
     * Cabecalho de resposta com quanto tempo o navegador guarda a
     * liberacao.
     */
    private static final String CABECALHO_MAX_AGE = "Access-Control-Max-Age";

    /**
     * Cabecalho de resposta que expoe os cabecalhos de rate limit
     * para o JavaScript.
     */
    private static final String CABECALHO_EXPOSTOS = "Access-Control-Expose-Headers";

    /**
     * Cabecalhos de rate limit, expostos para o frontend poder ler.
     */
    private static final String CABECALHOS_EXPOSTOS = "X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After, X-Correlation-Id";

    /**
     * Origens autorizadas.
     */
    private final Set<String> origensPermitidas;

    /**
     * Metodos permitidos.
     */
    private static final String METODOS = "GET, POST, PUT, DELETE, OPTIONS, PATCH";

    /**
     * Cabecalhos de requisicao permitidos.
     */
    private static final String CABECALHOS = "Content-Type, Authorization, Accept, Origin, X-Requested-With";

    public FiltroCorsGlobal(@Value("${app.cors.origins:http://localhost:8080,http://127.0.0.1:8080}") String origens) {
        this.origensPermitidas = new HashSet<>(Arrays.asList(origens.split(",")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String origem = request.getHeader("Origin");

        // A origem veio no pedido? Entao ela precisa ser autorizada.
        // Sem o cabecalho "Origin" (curl, outra ferramenta), nao ha
        // CORS a fazer - e a requisicao segue normal.
        boolean origemAutorizada = origem != null && origensPermitidas.contains(origem.trim());

        if (origemAutorizada) {
            response.setHeader(CABECALHO_ORIGEM, origem.trim());
            response.setHeader(CABECALHO_CREDENCIAIS, "true");
            response.setHeader(CABECALHO_METODOS, METODOS);
            response.setHeader(CABECALHO_CABECALHOS, CABECALHOS);
            response.setHeader(CABECALHO_EXPOSTOS, CABECALHOS_EXPOSTOS);
        }

        // O preflight: o navegador pergunta "posso mandar um PUT com
        // Authorization?". A resposta e' 200, curta, e NAO consome
        // vaga do limite de taxa - que e' o que estava happening
        // antes: cada chamada do frontend gastava duas vagas.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            if (origemAutorizada) {
                response.setHeader(CABECALHO_MAX_AGE, "3600");
            }

            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);

        // As respostas de erro que o proprio filtro de seguranca
        // montou (429, 403) ja' foram escritas. O CORS foi aplicado
        // antes, entao o navegador consegue ler o status.
    }
}
