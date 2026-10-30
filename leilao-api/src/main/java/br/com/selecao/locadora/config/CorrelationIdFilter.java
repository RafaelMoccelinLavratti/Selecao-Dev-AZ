package br.com.selecao.locadora.config;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Filtro que gera o identificador de correlacao da requisicao.
 *
 * E o que liga "o erro que o usuario viu na tela" com "as linhas de log do
 * servidor". O identificador vai no cabecalho X-Correlation-Id, no MDC (e
 * da para todas as linhas de log) e na resposta.
 *
 * Se o cliente ja mandou o cabecalho, o valor e reaproveitado - e
 * descartado se nao for um identificador valido, para nao dar entrada para
 * log forging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Nome do cabecalho HTTP que carrega o identificador.
     */
    public static final String CABECALHO = "X-Correlation-Id";

    /**
     * Chave usada no MDC (e no padrao de log do Logback).
     */
    public static final String CHAVE_MDC = "correlationId";

    /**
     * Tamanho maximo aceito do cabecalho recebido do cliente.
     */
    private static final int TAMANHO_MAXIMO = 64;

    /**
     * Atribui o identificador de correlacao a requisicao.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String identificador = identificadorDaRequisicao(request);

        // Disponibiliza no log: toda linha escrita nesta thread, enquanto
        // a requisicao estiver em andamento, carrega o mesmo identificador.
        MDC.put(CHAVE_MDC, identificador);

        // Devolve ao cliente: e o codigo que aparece na tela de erro e
        // que o suporte usa para localizar a requisicao.
        response.setHeader(CABECALHO, identificador);

        try {
            chain.doFilter(request, response);
        } finally {
            // Limpa o MDC sempre. Sem isso, o ID vazaria pra requisicao
            // seguinte (que roda na mesma thread do container) e o
            // rastreamento seria enganoso.
            MDC.remove(CHAVE_MDC);
        }
    }

    /**
     * Descobre o identificador da requisicao.
     *
     * @param request requisicao HTTP
     * @return o identificador recebido (saneado) ou um novo gerado
     */
    private String identificadorDaRequisicao(HttpServletRequest request) {

        String recebido = request.getHeader(CABECALHO);

        if (recebido != null && ehSeguro(recebido)) {
            return recebido;
        }

        return UUID.randomUUID().toString();
    }

    /**
     * Verifica se o valor recebido pode ser usado com seguranca.
     *
     * Aceita apenas letras, digitos, "-" e "_". Qualquer outro caractere
     * (principalmente CR e LF, que permitiriam injecar linhas falsas no
     * log) faz o valor ser descartado e um novo ID ser gerado.
     *
     * @param valor o valor recebido no cabecalho
     * @return true se o valor e seguro
     */
    private boolean ehSeguro(String valor) {

        if (valor == null || valor.isEmpty() || valor.length() > TAMANHO_MAXIMO) {
            return false;
        }

        for (int i = 0; i < valor.length(); i++) {

            char c = valor.charAt(i);

            boolean permitido = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';

            if (!permitido) {
                return false;
            }
        }

        return true;
    }
}
