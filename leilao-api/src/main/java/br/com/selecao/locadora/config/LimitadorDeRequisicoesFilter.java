package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de requisicoes por IP.
 *
 * E o complemento do controle anti-forca-bruta: aquele conta falhas de
 * login, este limita o volume total de requisicoes de um IP.
 *
 * A contagem usa janela deslizante (o que aconteceu nos ultimos N
 * segundos), e o excesso responde 429 com Retry-After.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimitadorDeRequisicoesFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(LimitadorDeRequisicoesFilter.class);

    /**
     * Cabecalho que informa ao cliente quanto tempo esperar.
     */
    private static final String CABECALHO_RETRY_AFTER = "Retry-After";

    /**
     * Cabecalho que informa o limite aplicavel.
     */
    private static final String CABECALHO_LIMITE = "X-RateLimit-Limit";

    /**
     * Quantidade de requisicoes restantes na janela.
     */
    private static final String CABECALHO_RESTANTES = "X-RateLimit-Remaining";

    /**
     * Contadores por IP.
     *
     * ConcurrentHashMap + AtomicInteger: a API atende varias requisicoes
     * ao mesmo tempo (o Tomcat usa um pool de threads), e dois
     * acessos simultaneos a um HashMap comum corromperiam a estrutura.
     */
    private final Map<String, Contador> contadores = new ConcurrentHashMap<>();

    /**
     * Indica se o limitador esta ativo.
     */
    @Value("${app.security.rate-limit.api.enabled:true}")
    private boolean habilitado;

    /**
     * Maximo de requisicoes por IP dentro da janela.
     */
    @Value("${app.security.rate-limit.api.max-requests:120}")
    private int maximoRequisicoes;

    /**
     * Duracao da janela deslizante, em segundos.
     */
    @Value("${app.security.rate-limit.api.window-seconds:60}")
    private long janelaSegundos;

    /**
     * Indica se a aplicacao esta atras de um proxy reverso confiavel.
     */
    @Value("${app.security.trust-proxy:false}")
    private boolean confiarEmProxy;

    /**
     * Aplica o limite a cada requisicao.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!habilitado) {
            chain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolver(request, confiarEmProxy);

        Contador contador = contadores.compute(ip, (chave, existente) -> {

            long agora = System.currentTimeMillis();

            if (existente == null || existente.expirada(agora)) {
                return new Contador(1, agora + janelaSegundos * 1000L);
            }

            existente.quantidade.incrementAndGet();
            existente.expiraEm = agora + janelaSegundos * 1000L;
            return existente;
        });

        // Quantas requisicoes ainda restam na janela, depois desta.
        // Com limite 3: apos a 1a -> 2 restam; apos a 3a -> 0 restam.
        int restantes = Math.max(maximoRequisicoes - contador.quantidade.get(), 0);
        long segundosRestantes = Math.max((contador.expiraEm - System.currentTimeMillis()) / 1000L, 0);

        // Cabecalhos informativos, presentes mesmo quando a requisicao
        // passa. Eles permitem ao cliente se auto-regular em vez de
        // descobrir o limite so quando o erro 429 acontece.
        response.setHeader(CABECALHO_LIMITE, String.valueOf(maximoRequisicoes));
        response.setHeader(CABECALHO_RESTANTES, String.valueOf(restantes));

        // A requisicao que estoura o limite e barrada - ou seja, a que
        // ja passa do limite. Com limite 3, as tres primeiras passam e a
        // quarta e barrada.
        if (contador.quantidade.get() > maximoRequisicoes) {
            LOG.warn("RATE LIMIT | Requisicao excedeu o limite | ip={} limite={} janela_s={}",
                    ip, maximoRequisicoes, janelaSegundos);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(CABECALHO_RETRY_AFTER, String.valueOf(segundosRestantes));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"erro\":\"Muitas requisicoes\","
                            + "\"mensagem\":\"Limite de requisicoes excedido. Tente novamente em "
                            + segundosRestantes + " segundos.\"}");

            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Zera todos os contadores.
     */
    public void limpar() {
        contadores.clear();
    }

    /**
     * Estrutura de contagem de um IP dentro da janela deslizante.
     */
    private static class Contador {

        /**
         * Quantidade de requisicoes na janela atual.
         */
        private final AtomicInteger quantidade = new AtomicInteger();

        /**
         * Instante (em milissegundos) em que a janela expira.
         */
        private volatile long expiraEm;

        Contador(int primeira, long expiraEm) {
            this.quantidade.set(primeira);
            this.expiraEm = expiraEm;
        }

        boolean expirada(long agora) {
            return agora >= expiraEm;
        }
    }
}
