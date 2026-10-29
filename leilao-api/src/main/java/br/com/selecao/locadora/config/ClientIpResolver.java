package br.com.selecao.locadora.config;

import javax.servlet.http.HttpServletRequest;

/**
 * ============================================================================
 *  UTILITARIO: DESCOBERTA DO ENDERECO IP REAL DO CLIENTE
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE ISTO EXISTE?
 *  ------------------------------------------------------
 *  O controle anti-forca-bruta e por IP. Para funcionar, precisamos saber
 *  qual e o IP de quem fez a requisicao. E aqui mora uma pegadinha:
 *
 *  HttpServletRequest.getRemoteAddr() devolve o IP do socket, ou seja, de
 *  quem fez a conexao TCP. Em desenvolvimento (rodando direto na sua
 *  maquina) isso e perfeito. Mas em producao, quase sempre existe um
 *  proxy reverso (Nginx, Apache, balanceador de carga na nuvem) na frente
 *  da aplicacao. Nesse caso, o IP que chega em getRemoteAddr() e o IP do
 *  PROXY - sempre o mesmo - e o IP real do usuario esta no cabecalho
 *  HTTP "X-Forwarded-For".
 *
 *  ------------------------------------------------------
 *  O CUIDADO DE SEGURANCA (importante!)
 *  ------------------------------------------------------
 *  O cabecalho "X-Forwarded-For" e um cabecalho HTTP COMUM: qualquer
 *  cliente pode enviar o que quiser. Se confiassemos nele cegamente, o
 *  atacante burlaria o bloqueio de forca bruta simplesmente trocando o
 *  valor do cabecalho a cada tentativa:
 *
 *      curl -H "X-Forwarded-For: 1.2.3.4" ...
 *      curl -H "X-Forwarded-For: 5.6.7.8" ...
 *
 *  Cada tentativa pareceria vir de um IP diferente, e o contador nunca
 *  estouraria. O controle anti-forca-bruta viraria decoracao.
 *
 *  A SOLUCAO USADA AQUI:
 *  So confiamos no X-Forwarded-For quando a aplicacao SABE que esta
 *  atras de um proxy confiavel - o que e configurado pela propriedade
 *  "app.security.trust-proxy" (ligada apenas no perfil de producao, junto
 *  com o "server.forward-headers-strategy=framework" que ja existe em
 *  application-prod.properties).
 *
 *  Sem proxy confiavel, usamos sempre getRemoteAddr(), que nao pode ser
 *  falsificado pelo cliente.
 *
 *  ------------------------------------------------------
 *  COMO FUNCIONA O X-FORWARDED-FOR
 *  ------------------------------------------------------
 *  Quando existem varios proxies, o cabecalho traz uma LISTA de IPs, do
 *  mais proximo para o mais distante:
 *
 *      X-Forwarded-For: 203.0.113.9, 70.41.3.18, 150.172.238.178
 *                        ^ cliente   ^ proxy1    ^ proxy2
 *
 *  O primeiro da lista e o IP real do cliente. Pegamos sempre o primeiro.
 * ============================================================================
 */
public final class ClientIpResolver {

    /**
     * Nome do cabecalho HTTP que carrega a cadeia de proxies.
     */
    public static final String CABECALHO_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * Nome do cabecalho alternativo, enviado por alguns CDN e provedores
     * de nuvem (Cloudflare, por exemplo, usa "CF-Connecting-IP").
     */
    public static final String CABECALHO_X_REAL_IP = "X-Real-IP";

    /**
     * Classe utilitaria: nao pode ser instanciada.
     */
    private ClientIpResolver() {
        throw new IllegalStateException("Classe utilitaria: nao deve ser instanciada.");
    }

    /**
     * Descobre o endereco IP de origem da requisicao.
     *
     * @param request        requisicao HTTP em andamento
     * @param confiarEmProxy true se a aplicacao esta atras de um proxy
     *                       confiavel (producao); false em desenvolvimento
     * @return o endereco IP do cliente (nunca nulo)
     */
    public static String resolver(HttpServletRequest request, boolean confiarEmProxy) {

        if (confiarEmProxy) {

            // ---- CAMINHO 1: cabecalho X-Forwarded-For (cadeia de proxies) ----
            String cadeia = request.getHeader(CABECALHO_X_FORWARDED_FOR);

            if (cadeia != null && !cadeia.trim().isEmpty()) {
                // O primeiro IP da lista e o cliente original.
                String primeiro = cadeia.split(",")[0].trim();

                if (!primeiro.isEmpty()) {
                    return primeiro;
                }
            }

            // ---- CAMINHO 2: cabecalho X-Real-IP (proxies mais simples) ----
            String ipReal = request.getHeader(CABECALHO_X_REAL_IP);

            if (ipReal != null && !ipReal.trim().isEmpty()) {
                return ipReal.trim();
            }
        }

        // ---- CAMINHO 3 (padrao): IP do socket ----
        // Nao pode ser falsificado pelo cliente. E o que usamos quando
        // NAO existe proxy confiavel na frente da aplicacao.
        String remoto = request.getRemoteAddr();

        return (remoto == null || remoto.trim().isEmpty()) ? "desconhecido" : remoto;
    }
}
