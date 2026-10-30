package br.com.selecao.locadora.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do limitador de requisicoes por IP.
 *
 * Um defeito aqui tem dois formatos, e os dois sao graves: deixar passar
 * quem estourou o limite, ou bloquear o IP errado. Os testes cobrem o 429, o
 * Retry-After com tempo real e o isolamento entre IPs.
 */
class LimitadorDeRequisicoesFilterTest {

    /**
     * Filtro sob teste.
     */
    private LimitadorDeRequisicoesFilter filtro;

    @BeforeEach
    void preparar() {

        filtro = new LimitadorDeRequisicoesFilter();

        // Configuracao minima: 3 requisicoes por janela de 60 segundos.
        ReflectionTestUtils.setField(filtro, "habilitado", true);
        ReflectionTestUtils.setField(filtro, "maximoRequisicoes", 3);
        ReflectionTestUtils.setField(filtro, "janelaSegundos", 60L);
        ReflectionTestUtils.setField(filtro, "confiarEmProxy", false);
    }

    /**
     * Teste 1: requisicoes DENTRO do limite passam normalmente.
     */
    @Test
    @DisplayName("Requisicoes dentro do limite passam normalmente")
    void requisicoesDentroDoLimitePassam() throws ServletException, IOException {

        for (int i = 1; i <= 3; i++) {

            MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/unidades");
            requisicao.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse resposta = new MockHttpServletResponse();

            AtomicInteger controllerChamado = new AtomicInteger();

            filtro.doFilter(requisicao, resposta, (req, res) -> controllerChamado.incrementAndGet());

            assertEquals(200, resposta.getStatus(),
                    "a requisicao " + i + " esta dentro do limite e deve passar");
            assertEquals(1, controllerChamado.get(),
                    "o controller deve ser chamado na requisicao " + i);
        }
    }

    /**
     * Teste 2: a requisicao ACIMA do limite e barrada com 429.
     */
    @Test
    @DisplayName("A requisicao acima do limite e barrada com 429")
    void requisicaoAcimaDoLimiteEBarrada() throws ServletException, IOException {

        // Esgota o limite.
        for (int i = 0; i < 3; i++) {
            executar("10.0.0.1");
        }

        MockHttpServletResponse resposta = executar("10.0.0.1");

        assertEquals(429, resposta.getStatus(),
                "a quarta requisicao deve ser barrada com 429 (Too Many Requests)");
    }

    /**
     * Teste 3: a resposta traz "Retry-After" com um tempo REAL.
     *
     * Um "Retry-After: 0" mandaria o cliente repetir imediatamente - o
     * que nao pouparia ninguem e ainda sobrecarregaria o servidor.
     */
    @Test
    @DisplayName("A resposta 429 traz Retry-After com tempo de espera real")
    void respostaTrazRetryAfterReal() throws ServletException, IOException {

        for (int i = 0; i < 3; i++) {
            executar("10.0.0.1");
        }

        MockHttpServletResponse resposta = executar("10.0.0.1");

        String retryAfter = resposta.getHeader("Retry-After");

        assertNotNull(retryAfter, "a resposta 429 deve trazer o cabecalho Retry-After");

        int segundos = Integer.parseInt(retryAfter);

        assertTrue(segundos > 0, "o tempo de espera deve ser positivo, veio: " + segundos);
        assertTrue(segundos <= 60, "o tempo de espera nao pode passar da janela, veio: " + segundos);
    }

    /**
     * Teste 4: o limite e POR IP - outro IP nao e afetado.
     *
     * Este e' o teste que protege o usuario legitimo: um cliente atras de
     * uma rede compartilhada (escritorio, operadora) nao pode ser
     * penalizado por causa de outro cliente da mesma rede.
     */
    @Test
    @DisplayName("O limite e por IP: outro IP nao e afetado")
    void limiteEPorIp() throws ServletException, IOException {

        // Esgota o limite do primeiro IP.
        for (int i = 0; i < 3; i++) {
            executar("10.0.0.1");
        }

        // O segundo IP nao pode ter sido afetado.
        MockHttpServletResponse outro = executar("10.0.0.2");

        assertEquals(200, outro.getStatus(),
                "um IP diferente nao pode ser bloqueado pelas falhas de outro");
    }

    /**
     * Teste 5: os cabecalhos informativos dizem o limite e o que resta.
     *
     * E o que permite ao cliente se auto-regular em vez de descobrir o
     * limite so quando o erro 429 acontece.
     */
    @Test
    @DisplayName("A resposta informa o limite e as requisicoes restantes")
    void respostaInformaLimiteERestantes() throws ServletException, IOException {

        MockHttpServletResponse primeira = executar("10.0.0.3");

        assertEquals("3", primeira.getHeader("X-RateLimit-Limit"),
                "o cabecalho deve informar o limite aplicavel");
        assertEquals("2", primeira.getHeader("X-RateLimit-Remaining"),
                "apos a primeira requisicao, devem restar 2");
    }

    /**
     * Executa uma requisicao e devolve a resposta.
     */
    private MockHttpServletResponse executar(String ip) throws ServletException, IOException {

        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/unidades");
        requisicao.setRemoteAddr(ip);
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) -> { });

        return resposta;
    }
}
