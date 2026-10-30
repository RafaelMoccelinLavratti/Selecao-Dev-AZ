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
