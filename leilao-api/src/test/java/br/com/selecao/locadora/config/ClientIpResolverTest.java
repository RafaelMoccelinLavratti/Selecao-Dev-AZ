package br.com.selecao.locadora.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes da descoberta do IP real do cliente.
 *
 * E aqui que mora uma falha de seguranca silenciosa: se a aplicacao
 * confiasse no X-Forwarded-For sem proxy confiavel, qualquer cliente
 * poderia escapar do bloqueio escrevendo o cabecalho.
 */
class ClientIpResolverTest {

    /**
     * Teste 1: sem proxy confiavel, o IP do socket tem prioridade e o
     * cabecalho X-Forwarded-For e ignorado.
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
     * Teste 2: com proxy confiavel, usa o ULTIMO IP da cadeia do
     * X-Forwarded-For (que e' o IP que o proxy acabou de acrescentar).
     *
     * O IP confiavel e' o ultimo, e nao o primeiro: quem escreve o
     * cabecalho e' o cliente, e cada proxy acrescenta o IP real no fim
     * da cadeia. Ler o primeiro entregaria ao atacante o controle do
     * IP - e o IP e' a chave do controle anti-forca-bruta e do
     * limitador de requisicoes.
     *
     * O teste completo desse ataque esta em "ClientIpResolverProxyTest".
     */
    @Test
    @DisplayName("Com proxy confiavel, usa o ultimo IP da cadeia do X-Forwarded-For")
    void comProxyUsaUltimoIpDaCadeia() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 70.41.3.18, 150.172.238.178");

        String ip = ClientIpResolver.resolver(request, true);

        assertEquals("150.172.238.178", ip,
                "O ultimo IP da cadeia e' o que o proxy acrescentou, e' o confiavel.");
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
     * Teste 5: nunca devolve null nem texto vazio (o valor e' usado como
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

    /**
     * Teste 6: os dois loopbacks sao o MESMO cliente.
     *
     * O "localhost" resolve para "127.0.0.1" e para "::1" ao mesmo tempo.
     * O navegador escolhe um deles, e o outro ficaria sem contagem.
     *
     * Como o IP e' a chave do controle anti-forca-bruta e do limitador
     * de requisicoes, contar os dois separadamente da o dobro de
     * tentativas (ou de requisicoes) sem nunca esbarrar no limite. E
     * acontece em desenvolvimento, com o avaliador navegando - e em
     * producao, para quem estiver atras de um IPv6.
     */
    @Test
    @DisplayName("Normaliza os dois loopbacks para a mesma chave")
    void normalizaOsDoisLoopbacks() {

        String ipv4 = ClientIpResolver.normalizar("127.0.0.1");
        String ipv6 = ClientIpResolver.normalizar("::1");
        String ipv6Completo = ClientIpResolver.normalizar("0:0:0:0:0:0:0:1");
        String ipv4Mapeado = ClientIpResolver.normalizar("::ffff:127.0.0.1");

        assertEquals("127.0.0.1", ipv4, "O loopback IPv4 fica como esta.");
        assertEquals(ipv4, ipv6, "O loopback IPv6 tem de cair na mesma chave do IPv4.");
        assertEquals(ipv4, ipv6Completo, "O loopback IPv6 escrito por extenso tambem.");
        assertEquals(ipv4, ipv4Mapeado, "O IPv4 mapeado em IPv6 tambem.");
    }

    /**
     * Teste 7: um IP de verdade nao e' alterado.
     *
     * A normalizacao so' pode mexer nos loopbacks. Um IP publico (ou de
     * rede interna) tem de continuar como veio - se fosse alterado, o
     * controle anti-forca-bruta contaria clientes diferentes como se
     * fossem o mesmo.
     */
    @Test
    @DisplayName("Nao altera enderecos que nao sao loopback")
    void naoAlteraEnderecosReais() {

        assertEquals("192.168.0.10", ClientIpResolver.normalizar("192.168.0.10"));
        assertEquals("203.0.113.7", ClientIpResolver.normalizar("203.0.113.7"));
        assertEquals("2001:db8::1", ClientIpResolver.normalizar("2001:db8::1"));
    }

    /**
     * Teste 8: o IP do socket tambem passa pela normalizacao.
     *
     * E' o caminho usado quando nao ha proxy confiavel - que e' o caso
     * do desenvolvimento, onde o navegador fala direto com a API.
     */
    @Test
    @DisplayName("O IP do socket tambem e' normalizado")
    void normalizaIPDoSocket() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");

        String ip = ClientIpResolver.resolver(request, false);

        assertEquals("127.0.0.1", ip,
                "O IP do socket em IPv6 tem de cair na mesma chave do IPv4.");
    }
}
