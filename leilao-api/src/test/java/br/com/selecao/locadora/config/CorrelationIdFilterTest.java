package br.com.selecao.locadora.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 *  TESTE: IDENTIFICADOR DE CORRELACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE ESTE TESTE EXISTE?
 *  ------------------------------------------------------
 *  O identificador de correlacao e o que liga "o erro que o usuario viu na
 *  tela" com "as linhas de log do servidor". Se ele nao for unico, ou se
 *  vazar de uma requisicao para a seguinte, o rastreamento vira
 * udio - e o time de TI perde horas procurando a causa.
 *
 *  Por isso o teste cobre os TRES riscos reais:
 *
 *      1) o ID precisa ser unico por requisicao;
 *      2) o ID precisa NAO vazar para a requisicao seguinte (a limpeza do
 *         MDC no "finally");
 *      3) o valor vindo do cliente precisa ser SANITIZADO - senao um
 *         atacante injeta linhas falsas no log (log forging).
 * ============================================================================
 */
class CorrelationIdFilterTest {

    /**
     * Filtro sob teste.
     */
    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @AfterEach
    void limpar() {
        MDC.remove(CorrelationIdFilter.CHAVE_MDC);
    }

    /**
     * Teste 1: cada requisicao recebe um identificador.
     */
    @Test
    @DisplayName("Cada requisicao recebe um identificador de correlacao")
    void cadaRequisicaoRecebeUmIdentificador() throws ServletException, IOException {

        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/unidades");
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        String[] idVistoPeloFiltro = new String[1];

        filtro.doFilter(requisicao, resposta, (req, res) ->
                idVistoPeloFiltro[0] = MDC.get(CorrelationIdFilter.CHAVE_MDC));

        assertNotNull(idVistoPeloFiltro[0], "o MDC deve estar preenchido durante a requisicao");
        assertEquals(idVistoPeloFiltro[0], resposta.getHeader(CorrelationIdFilter.CABECALHO),
                "o cabecalho de resposta deve trazer o mesmo identificador");
    }

    /**
     * Teste 2: o identificador NAO vaza para a requisicao seguinte.
     *
     * Este e o teste mais importante do arquivo. Sem a limpeza no
     * "finally", o MDC ficaria com o valor da requisicao anterior - e o
     * log mostraria o ID errado, orientando otime de TI para o lado errado.
     */
    @Test
    @DisplayName("O identificador nao vaza para a requisicao seguinte")
    void identificadorNaoVazaParaAProximaRequisicao() throws ServletException, IOException {

        MockHttpServletRequest primeira = new MockHttpServletRequest("GET", "/unidades");
        MockHttpServletResponse respostaPrimeira = new MockHttpServletResponse();

        filtro.doFilter(primeira, respostaPrimeira, (req, res) -> { });

        // Depois da requisicao, o MDC tem de estar limpo.
        assertTrue(MDC.get(CorrelationIdFilter.CHAVE_MDC) == null,
                "o MDC deve ser limpo ao fim da requisicao");

        // E a proxima requisicao nao pode herdar o ID da anterior.
        MockHttpServletRequest segunda = new MockHttpServletRequest("GET", "/leiloes");
        MockHttpServletResponse respostaSegunda = new MockHttpServletResponse();

        String[] idDaSegunda = new String[1];
        filtro.doFilter(segunda, respostaSegunda, (req, res) ->
                idDaSegunda[0] = MDC.get(CorrelationIdFilter.CHAVE_MDC));

        assertNotNull(idDaSegunda[0]);
        assertNotEquals(respostaPrimeira.getHeader(CorrelationIdFilter.CABECALHO),
                respostaSegunda.getHeader(CorrelationIdFilter.CABECALHO),
                "cada requisicao deve ter o seu proprio identificador");
    }

    /**
     * Teste 3: o identificador recebido do cliente e REAPROVEITADO.
     *
     * E o que permite ligar o log de varios servicos: o "servico A" gera
     * o ID e o passa para o "servico B", e os dois logs ficam ligados.
     */
    @Test
    @DisplayName("O identificador enviado pelo cliente e reaproveitado")
    void identificadorDoClienteEReaproveitado() throws ServletException, IOException {

        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/unidades");
        requisicao.addHeader(CorrelationIdFilter.CABECALHO, "abc-123_XYZ");

        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) -> { });

        assertEquals("abc-123_XYZ", resposta.getHeader(CorrelationIdFilter.CABECALHO),
                "o identificador valido do cliente deve ser preservado");
    }

    /**
     * Teste 4: um identificador MALICIOSO e descartado.
     *
     * O valor "injection\\nLOG FALSO | nivel=ERROR" contem quebra de linha.
     * Se fosse aceito, o atacante conseguiria escrever LINHAS FALSAS no
     * arquivo de log - e ainda poderia forjar o formato das linhas reais,
     * para o log parecer legitimo.
     */
    @Test
    @DisplayName("Um identificador malicioso e descartado e substituido")
    void identificadorMaliciosoEDescartado() throws ServletException, IOException {

        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/unidades");
        requisicao.addHeader(CorrelationIdFilter.CABECALHO, "injection\nLOG FALSO | nivel=ERROR");

        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) -> { });

        String cabecalho = resposta.getHeader(CorrelationIdFilter.CABECALHO);

        assertNotNull(cabecalho);
        assertNotEquals("injection\nLOG FALSO | nivel=ERROR", cabecalho,
                "o valor malicioso nao pode ser aceito");
        assertFalse(cabecalho.contains("\n"), "o identificador nao pode conter quebra de linha");
        assertFalse(cabecalho.contains(" "), "o identificador nao pode conter espaco");
    }

    /**
     * Teste 5: um identificador LONGO demais e descartado.
     */
    @Test
    @DisplayName("Um identificador longo demais e descartado")
    void identificadorLongoEDescartado() throws ServletException, IOException {

        StringBuilder enorme = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            enorme.append("A");
        }

        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/unidades");
        requisicao.addHeader(CorrelationIdFilter.CABECALHO, enorme.toString());

        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) -> { });

        String cabecalho = resposta.getHeader(CorrelationIdFilter.CABECALHO);

        assertNotNull(cabecalho);
        assertTrue(cabecalho.length() <= 64, "o identificador gerado deve respeitar o limite de tamanho");
        assertFalse(cabecalho.equals(enorme.toString()));
    }
}
