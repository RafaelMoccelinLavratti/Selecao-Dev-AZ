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
 * ============================================================================
 *  FILTRO: IDENTIFICADOR DE CORRELACAO (CORRELATION ID)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O PROBLEMA QUE ESTE FILTRO RESOLVE
 *  ------------------------------------------------------
 *  Imagina o seguinte cenario: o usuario clica em "salvar" na tela de
 *  empresas e a tela fica carregando. O front-end mostra "algo deu erro".
 *  O usuario liga para o suporte e diz "deu erro ao salvar".
 *
 *  O suporte olha o log do servidor. Ha mil requisicoes por segundo, de
 *  varios usuarios, de varios ambientes. Como saber QUAL requisicao foi a
 *  do usuario que ligou?
 *
 *  A resposta classica: cada requisicao recebe um identificador unico, e
 *  esse identificador aparece:
 *      - no log do servidor (em TODAS as linhas da requisicao);
 *      - no cabecalho de resposta "X-Correlation-Id";
 *      - na tela de erro do usuario.
 *
 *  Com isso, o suporte pede: "me manda o codigo que apareceu no canto da
 *  tela" - e o time de TI filtra o log por aquele codigo e acha exatamente
 *  a requisicao, com todo o contexto.
 *
 *  Isso e o padrao "correlation id" (tambem chamado de "request id" ou
 *  "trace id"), e e o mesmo mecanismo que sistemas grandes usam para
 *  rastrear uma operacao entre varios servicos.
 *
 *  ------------------------------------------------------
 *  POR QUE USAR O "MDC"?
 *  ------------------------------------------------------
 *  O MDC (Mapped Diagnostic Context) e um recurso do SLF4J, usado pelo
 *  Logback, que guarda um conjunto de pares chave/valor Associate com a
 *  THREAD que esta executando. Ao escrever no log, o padrao
 *  "%X{correlationId}" imprime automaticamente o valor daquele contexto.
 *
 *  O detalhe importante: como o MDC e cleaned no final (no bloco
 *  finally), o valor NAO vaza para a requisicao seguinte. Sem esse
 *  cleanup, um ID de requisicao poderia aparecer em um log que nao tem
 *  nada a ver - e o rastreamento passaria a ser confuso em vez de util.
 *
 *  ------------------------------------------------------
 *  E O CLIENTE PODE ENVIAR O ID?
 *  ------------------------------------------------------
 *  Sim. Se a requisicao ja vier com o cabecalho "X-Correlation-Id",
 *  reaproveitamos o valor. Isso e essencial quando ha varios servicos:
 *  o "servico A" gera o ID e o passa para o "servico B", e o log dos
 *  dois fica ligado pelo mesmo identificador.
 *
 *  Mas com um cuidado: o valor precisa ser SANITIZADO. Sem isso, um
 *  cliente poderia mandar um cabecalho com quebras de linha e injetar
 *  linhas falsas no log (ataque de log injection / log forging). Por
 *  isso aceitamos apenas caracteres alfanumericos, "-" e "_", ate 64
 *  caracteres. Se o valor nao passar no filtro, geramos um novo.
 *
 *  ------------------------------------------------------
 *  ONDE ESTE FILTRO SE POSICIONA?
 *  ------------------------------------------------------
 *  Ele e o PRIMEIRO filtro do container de servlet (@Order com a maior
 *  prioridade). Isso e' proposital: o identificador precisa estar no
 *  contexto de log (MDC) ANTES de qualquer outro filtro escrever uma
 *  linha - inclusive das linhas de "requisicao barrada pelo limitador de
 *  volume", que sao justamente as mais importantes para investigar um
 *  ataque.
 * ============================================================================
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
            // SEMPRE limpa o MDC. Sem isso, o ID vazaria para a
            // requisicao seguinte (que rodaria na mesma thread do
            // container) e o rastreamento seria enganoso.
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
