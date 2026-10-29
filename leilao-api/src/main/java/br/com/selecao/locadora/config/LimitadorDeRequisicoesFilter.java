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
 * ============================================================================
 *  FILTRO: LIMITADOR DE REQUISICOES POR IP (RATE LIMITING)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  A DIFERENCA PARA O CONTROLE ANTI-FORCA-BRUTA
 *  ------------------------------------------------------
 *  Ja existe um filtro que bloqueia quem erra a senha (BruteForce-
 *  ProtectionFilter). Este e' outro: ele limita o VOLUME TOTAL de
 *  requisicoes vindas do mesmo IP, independentemente de serem de login ou
 *  nao.
 *
 *  A razao de os dois existirem e' que eles defendem coisas diferentes:
 *
 *      - o anti-forca-bruta protege o CONTRO DE ACESSO (a conta do
 *        usuario);
 *      - o rate limiting protege o RECURSO (a API inteira).
 *
 *  Um atacante pode nao querer nem tentar logar: ele pode simplesmente
 *  disparar requisicoes para derrubar a API (ataque de negacao de
 *  servico) ou consumir recursos de proposito (cada requisicao de
 *  leitura pode custar uma consulta pesada ao banco).
 *
 *  ------------------------------------------------------
 *  POR QUE O LIMITE E POR IP E NAO POR USUARIO?
 *  ------------------------------------------------------
 *  Porque o atacante nao esta autenticado (ou esta tentando nao ser
 *  identificado). O IP e' a unica identidade disponivel antes da
 *  autenticacao. E por isso que a RESOLUCAO DO IP PRECISA SER CONFIAVEL:
 *
 *      - o cabecalho "X-Forwarded-For" pode ser enviado por QUALQUER
 *        cliente. Se confiassemos nele sem condicao, o atacante burlaria
 *        o limite trocando o valor do cabecalho a cada requisicao;
 *      - por isso o controle so usa o X-Forwarded-For quando existe um
 *        proxy reverso CONFIAVEL na frente (ver ClientIpResolver e a
 *        propriedade "app.security.trust-proxy").
 *
 *  ------------------------------------------------------
 *  O ALGORITMO: JANELA DESLIZANTE
 *  ------------------------------------------------------
 *  Uma "janela deslizante" conta as requisicoes dentro dos ultimos N
 *  segundos, e nao dentro de um bloco fixo (como "100 por minuto, a
 *  contar do minuto cheio").
 *
 *  A diferenca importa: no bloco fixo, um atacante pode disparar 100
 *  requisicoes no ultimo segundo do minuto e outras 100 no primeiro
 *  segundo do minuto seguinte - o dobro do limite em um instante.
 *  Com a janela deslizante, o limite e' sempre respeitado.
 *
 *  ------------------------------------------------------
 *  POR QUE O RESPOSTA E 429 E NAO 503?
 *  ------------------------------------------------------
 *  429 Too Many Requests e' o codigo oficial do HTTP para "voce fez
 *  requisicoes demais". Ele informa ao cliente que a situacao e'
 *  TEMPORARIA e que ele deve tentar de novo - que e' a verdade. O 503
 *  (servico indisponivel) diria que o problema e' do servidor, e
 *  faria o cliente desistir.
 *
 *  O cabecalho "Retry-After" diz QUANDO tentar de novo, em segundos.
 *  E' uma pratica que reduz drasticamente a Pressao sobre o servidor:
 *  em vez de o cliente repetir imediatamente, ele espera.
 *
 *  ------------------------------------------------------
 *  ONDE ESTE FILTRO SE POSICIONA?
 *  ------------------------------------------------------
 *  Ele roda no CONTAINER DE SERVLET (e nao dentro da cadeia do Spring
 *  Security), por dois motivos:
 *
 *      1) ele e um concern TRANSVERSAL - precisa valer para TODAS as
 *         requisicoes, inclusive as de monitoracao (/actuator/health),
 *         que nao passam pela cadeia de seguranca;
 *      2) ele precisa barrar o volume ANTES de qualquer trabalho caro
 *         (consulta ao banco, geracao de token, validacao de senha).
 *
 *  A ordem (@Order) garante que ele rode depois do identificador de
 *  correlacao (que precisa estar no log de toda requisicao, inclusive
 *  nas que forem barradas por aqui) e antes dos demais filtros.
 * ============================================================================
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
     * Duracao da janela deslizante, em SEGUNDOS.
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

        // Quantas requisicoes ainda restam na janela, APOS esta.
        // Com limite 3: apos a 1a -> 2 restam; apos a 3a -> 0 restam.
        int restantes = Math.max(maximoRequisicoes - contador.quantidade.get(), 0);
        long segundosRestantes = Math.max((contador.expiraEm - System.currentTimeMillis()) / 1000L, 0);

        // Cabecalhos informativos, presentes mesmo quando a requisicao
        // passa. Eles permitem ao cliente se auto-regular em vez de
        // descobrir o limite so quando o erro 429 acontece.
        response.setHeader(CABECALHO_LIMITE, String.valueOf(maximoRequisicoes));
        response.setHeader(CABECALHO_RESTANTES, String.valueOf(restantes));

        // A REQUISICAO que estoura o limite e barrada - ou seja, a que
        // ja passa do limite. Com limite 3, as tres primeiras passam e a
        // quarta e barrada.
        if (contador.quantidade.get() > maximoRequisicoes) {
            LOG.warn("RATE LIMIT | Requisição excedeu o limite | ip={} limite={} janela_s={}",
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
