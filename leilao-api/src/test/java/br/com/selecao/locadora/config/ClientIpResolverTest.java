package br.com.selecao.locadora.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ============================================================================
 *  TESTE: DESCOBERTA DO IP REAL DO CLIENTE
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE ISTO MERECE UM TESTE?
 *  ------------------------------------------------------
 *  Porque e aqui que mora uma falha de seguranca silenciosa.
 *
 *  Se a aplicacao confiasse no cabecalho "X-Forwarded-For" sem
 *  condicao, o atacante burlaria o bloqueio de forca bruta assim:
 *
 *      curl -H "X-Forwarded-For: 1.2.3.4" ...
 *      curl -H "X-Forwarded-For: 5.6.7.8" ...
 *
 *  Cada tentativa pareceria vir de um IP diferente, o contador nunca
 *  estouraria e o controle anti-forca-bruta viraria decoracao.
 *
 *  Estes testes travam o comportamento correto:
 *  - SEM proxy confiavel: usa sempre o IP do socket (que o cliente nao
 *    consegue forjar);
 *  - COM proxy confiavel: usa o primeiro IP da cadeia do
 *    X-Forwarded-For.
 * ============================================================================
 */
class ClientIpResolverTest {

    /**
     * Teste 1: sem proxy confiavel, o IP do socket tem prioridade e o
     * cabecalho X-Forwarded-For e IGNORADO.
     */
    @Test
    @DisplayName("Sem proxy confiavel, ignora o X-Forwarded-For e usa o IP do socket")
    void semProxyUsaIPDoSocket() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.addHeader("X-Forwarded-For", "8.8.8.8");

        String ip = ClientIpResolver.resolver(request, false);

        assertEquals("192.168.0.10", ip,
                "Sem proxy confiavel, o IP forjado no cabecalho nao pode ser usado.");
    }

    /**
     * Teste 2: com proxy confiavel, usa o PRIMEIRO IP da cadeia do
     * X-Forwarded-For (que e o cliente original).
     */
    @Test
    @DisplayName("Com proxy confiavel, usa o primeiro IP da cadeia do X-Forwarded-For")
    void comProxyUsaPrimeiroIpDaCadeia() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 70.41.3.18, 150.172.238.178");

        String ip = ClientIpResolver.resolver(request, true);

        assertEquals("203.0.113.9", ip,
                "O primeiro IP da cadeia e o cliente original.");
    }

    /**
     * Teste 3: com proxy confiavel e SEM o cabecalho X-Forwarded-For,
     * recorre ao cabecalho X-Real-IP.
     */
    @Test
    @DisplayName("Com proxy confiavel, usa o X-Real-IP quando nao houver X-Forwarded-For")
    void comProxyUsaXRealIpComoAlternativa() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");

        String ip = ClientIpResolver.resolver(request, true);

        assertEquals("198.51.100.7", ip,
                "O X-Real-IP deve ser usado quando o X-Forwarded-For nao existir.");
    }

    /**
     * Teste 4: sem nenhum cabecalho e sem proxy, usa o IP do socket.
     */
    @Test
    @DisplayName("Sem cabecalhos, recorre ao IP do socket")
    void semCabecalhosUsaIPDoSocket() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.0.5");

        String ip = ClientIpResolver.resolver(request, true);

        assertEquals("172.16.0.5", ip,
                "Sem cabecalho de proxy, o IP do socket e a unica opcao.");
    }

    /**
     * Teste 5: nunca devolve null nem texto vazio (o valor e usado como
     * chave de um mapa - um null quebraria o controle de tentativas).
     */
    @Test
    @DisplayName("Nunca devolve IP nulo ou vazio")
    void nuncaDevolveValorVazio() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        String ip = ClientIpResolver.resolver(request, false);

        assertEquals("desconhecido", ip,
                "Sem IP disponivel, deve devolver um valor seguro e nao nulo.");
    }
}
