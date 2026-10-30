package br.com.selecao.locadora.config;

import javax.servlet.http.HttpServletRequest;

/**
 * Descobre o IP real de quem fez a requisicao.
 *
 * O controle anti-forca-bruta e por IP, entao o IP precisa ser confiavel. O
 * cabecalho X-Forwarded-For so e considerado quando a aplicacao sabe que
 * esta atras de um proxy confiavel (app.security.trust-proxy); caso
 * contrario, qualquer cliente poderia escrever nele e escapar do bloqueio.
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

            // Primeiro: cabecalho X-Forwarded-For (cadeia de proxies).
            String cadeia = request.getHeader(CABECALHO_X_FORWARDED_FOR);

            if (cadeia != null && !cadeia.trim().isEmpty()) {

                // O IP confiavel e' o ULTIMO elemento da cadeia.
                //
                // Isto e' o ponto que costuma ser feito ao contrario. O
                // cabecalho X-Forwarded-For e' construido assim: o
                // cliente escreve o que quiser, e cada proxy que a
                // requisicao atravessa ACRESCENTA o IP de quem ele viu.
                //
                //   cliente envia:   X-Forwarded-For: 10.0.0.1
                //   proxy acrescenta: X-Forwarded-For: 10.0.0.1, 203.0.113.7
                //                                                     ^^^^^^^^
                //                                          este e' o IP real
                //
                // O primeiro elemento e' o que o CLIENTE escolheu. Ler o
                // primeiro e' entregar o controle do IP ao atacante: como
                // o IP e' a chave do controle anti-forca-bruta e do
                // limitador de requisicoes, basta trocar esse valor a
                // cada chamada para nunca acumular contagem.
                //
                // O ultimo elemento e' o que o proxy acabou de acrescentar,
                // e portanto nao pode ter sido forjado pelo cliente.
                String ipConfiavel = ultimoIpDaCadeia(cadeia);

                if (ipConfiavel != null) {
                    return ipConfiavel;
                }
            }

            // Depois: cabecalho X-Real-IP (proxies mais simples).
            String ipReal = request.getHeader(CABECALHO_X_REAL_IP);

            if (ipReal != null && !ipReal.trim().isEmpty()) {
                return ipReal.trim();
            }
        }

        // Por ultimo: o IP do socket. Nao pode ser falsificado pelo
        // cliente, e e o que usamos quando nao existe proxy confiavel na
        // frente da aplicacao.
        String remoto = request.getRemoteAddr();

        return (remoto == null || remoto.trim().isEmpty()) ? "desconhecido" : remoto;
    }

    /**
     * Pega o ultimo IP valido da cadeia do X-Forwarded-For.
     *
     * Percorre a cadeia de tras para frente, ignorando as entradas
     * vazias. Percorrer de tras para frente (e nao pegar direto o
     * ultimo) protege contra um proxy mal configurado que mande a
     * cadeia com espacos ou entradas em branco no fim.
     *
     * @param cadeia o valor bruto do cabecalho X-Forwarded-For
     * @return o IP, ou null se a cadeia nao tiver nenhum valido
     */
    private static String ultimoIpDaCadeia(String cadeia) {

        String[] partes = cadeia.split(",");

        for (int i = partes.length - 1; i >= 0; i--) {

            String ip = partes[i].trim();

            if (!ip.isEmpty()) {
                return ip;
            }
        }

        return null;
    }
}
