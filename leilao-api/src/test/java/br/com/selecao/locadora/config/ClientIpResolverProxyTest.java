package br.com.selecao.locadora.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Testes de como o IP real e' descoberto quando a aplicacao esta atras de proxy.
 *
 * O ponto deste arquivo e um detalhe que passa despercebido: quem
 * escreve o cabecalho X-Forwarded-For e' o CLIENTE, e o proxy e' que
 * ACRESCENTA o IP real no final da cadeia.
 *
 * Exemplo de um ataque real:
 *
 *   1. O atacante envia  X-Forwarded-For: 10.0.0.1
 *   2. O proxy, que nao confia no cabecalho do cliente, acrescenta o IP
 *      verdadeiro:  X-Forwarded-For: 10.0.0.1, 203.0.113.7
 *   3. Se a aplicacao ler o PRIMEIRO elemento, ela usa 10.0.0.1 - que o
 *      atacante escolheu.
 *
 * Como o IP e' a chave do controle de tentativas e do limitador de
 * requisicoes, o atacante que varia esse valor a cada requisicao nunca
 * acumula contagem: o limite por IP deixa de valer.
 *
 * A correcao e' o inverso do que parece: quando existe um proxy
 * confiavel, o IP confiavel e' o ULTIMO elemento da cadeia (o que o
 * proxy acabou de acrescentar), nao o primeiro.
 */
class ClientIpResolverProxyTest {

    /**
     * Teste 1: o IP confiavel e' o ultimo elemento da cadeia.
     *
     * E o teste que trava a correcao. A cadeia abaixo tem um IP
     * forjado no comeco (o que o cliente escreveu) e o IP verdadeiro no
     * fim (o que o proxy acrescentou). O que a aplicacao tem de usar e'
     * o ultimo.
     */
    @Test
    @DisplayName("Com proxy confiavel, o IP e' o ULTIMO da cadeia (o que o proxy acrescentou)")
    void comProxyUsaUltimoIpDaCadeia() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        // O cliente forjou o primeiro; o proxy acrescentou o ultimo.
        request.addHeader("X-Forwarded-For", "10.0.0.1, 203.0.113.7");

        String ip = ClientIpResolver.resolver(request, true);

        assertEquals("203.0.113.7", ip,
                "O IP confiavel e' o ultimo da cadeia: e' o proxy que o acrescenta. "
                        + "O primeiro e' controlado pelo cliente.");
    }

    /**
     * Teste 2: o IP forjado pelo cliente nunca e' o usado.
     *
     * Este e' o teste do ataque. O cliente manda um IP inventado e
     * verifica que ele nao vaza para a aplicacao. Sem esta garantia, o
     * limitador de requisicoes e o controle de tentativas ficam sem
     * efeito: basta trocar o cabecalho a cada requisicao.
     */
    @Test
    @DisplayName("O IP forjado pelo cliente nao e' o usado (o limite por IP nao pode ser burlado)")
    void ipForjadoPeloClienteNaoEhUsado() {

        String ipForjado = "10.0.0.1";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", ipForjado + ", 203.0.113.7");

        String ip = ClientIpResolver.resolver(request, true);

        assertNotEquals(ipForjado, ip,
                "O IP que o cliente escreveu no cabecalho nao pode ser o IP usado. "
                        + "Senao o atacante burla o limite por IP trocando o cabecalho.");
    }

    /**
     * Teste 3: variar o IP forjado nao muda o IP real.
     *
     * Este e' o ataque completo. O atacante faz varias requisicoes,
     * cada uma com um IP forjado diferente. Se o IP resolvido mudasse a
     * cada vez, cada requisicao cairia num balde novo do limitador - e
     * o limite nunca seria atingido.
     *
     * Com a correcao, todas as requisicoes do mesmo cliente real caem
     * no mesmo balde, e o limite volta a valer.
     */
    @Test
    @DisplayName("Variar o IP forjado nao escapa do limite (o IP real e' sempre o mesmo)")
    void variarIpForjadoNaoEscapaDoLimite() {

        String[] ipsForjados = {"10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5"};

        String ipRealEsperado = null;

        for (String forjado : ipsForjados) {

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", forjado + ", 203.0.113.7");

            String ip = ClientIpResolver.resolver(request, true);

            if (ipRealEsperado == null) {
                ipRealEsperado = ip;
            }

            assertEquals(ipRealEsperado, ip,
                    "Trocar o IP forjado nao pode trocar o IP usado: seria o jeito "
                            + "mais facil de burlar o limite por IP.");
        }
    }

    /**
     * Teste 4: com um unico proxy, o IP e' o proprio valor do cabecalho.
     *
     * Quando o cliente nao mandou nada e o proxy acrescentou so o IP
     * real, a cadeia tem um elemento so. Esse elemento e' confiavel, e
     * o codigo tem de usa-lo.
     */
    @Test
    @DisplayName("Com um unico IP na cadeia, usa esse IP")
    void comUmUnicoIpNaCadeiaUsaEsseIp() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("203.0.113.7", ClientIpResolver.resolver(request, true),
                "Com um proxy so, o IP do cabecalho e' o IP real.");
    }

    /**
     * Teste 5: espacos em volta do IP nao atrapalham.
     */
    @Test
    @DisplayName("Espacos em volta do IP sao removidos")
    void removeEspacosEmVoltaDoIp() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "  203.0.113.7  ");

        assertEquals("203.0.113.7", ClientIpResolver.resolver(request, true),
                "O IP deve vir limpo, sem espacos.");
    }

    /**
     * Teste 6: cadeia com elementos vazios nao quebra nada.
     *
     * Um proxy mal configurado pode mandar "1.2.3.4, , 5.6.7.8". O
     * codigo tem de pular o elemento vazio em vez de devolver "".
     */
    @Test
    @DisplayName("Cadeia com elemento vazio e' tratada sem quebrar")
    void cadeiaComElementoVazioNaoQuebra() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, , 5.6.7.8");

        String ip = ClientIpResolver.resolver(request, true);

        assertNotEquals("", ip, "A cadeia nao pode devolver IP vazio.");
        assertNotEquals(null, ip, "A cadeia nao pode devolver IP nulo.");
    }

    /**
     * Teste 7: sem proxy confiavel, o cabecalho e' ignorado por completo.
     *
     * Este e' o comportamento que garante que, sem proxy na frente, o
     * cliente nao consegue escapar do bloqueio por IP.
     */
    @Test
    @DisplayName("Sem proxy confiavel, o cabecalho forjado e' ignorado")
    void semProxyIgnoraCabecalhoForjado() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 203.0.113.7");

        assertEquals("192.168.0.10", ClientIpResolver.resolver(request, false),
                "Sem proxy confiavel, so o IP do socket vale.");
    }
}
