package br.com.selecao.locadora.config;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Cabecalhos de seguranca da resposta HTTP.
 *
 * Sao cabecalhos que o navegador respeita para bloquear certos ataques no
 * lado do cliente: Content-Security-Policy, X-Frame-Options,
 * X-Content-Type-Options, Referrer-Policy, Permissions-Policy e os
 * cabecalhos de isolamento de origem (COOP, CORP, COEP).
 */
@Component
public class SecurityHeadersFilter implements Filter {

    /**
     * Content-Security-Policy da API.
     *
     * "default-src 'none'" significa: nao carregue nada de nenhuma
     * origem, a menos que esteja explicitamente liberado. Como a API
     * devolve apenas JSON, isso nao afeta o frontend, que roda em outra
     * porta (e portanto outra origem) e tem sua propria politica.
     *
     * "'frame-ancestors 'none'" completa o X-Frame-Options: e a
     * diretiva moderna que impede o clickjacking, e funciona tambem
     * onde o X-Frame-Options nao e' suportado.
     */
    private static final String CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    /**
     * Permissions-Policy: desliga tudo o que a API nao usa.
     */
    private static final String PERMISSIONS_POLICY =
            "accelerometer=(), autoplay=(), camera=(), display-capture=(), "
                    + "encrypted-media=(), fullscreen=(), geolocation=(), gyroscope=(), "
                    + "magnetometer=(), microphone=(), midi=(), payment=(), "
                    + "picture-in-picture=(), publickey-credentials-get=(), screen-wake-lock=(), "
                    + "sync-xhr=(), usb=(), xr-spatial-tracking=()";

    /**
     * Aplica os cabecalhos de seguranca em toda resposta.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Bloqueia o "adivinhacao" de tipo de conteudo.
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        // Bloqueia o embutimento em iframe de outro site (clickjacking).
        httpResponse.setHeader("X-Frame-Options", "DENY");
        // Politica de conteudo: nenhuma origem pode fornecer recurso.
        httpResponse.setHeader("Content-Security-Policy", CSP);
        // Desliga as APIs de navegador que a API nao usa.
        httpResponse.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        // Isola a aba: impede o sequestro de janela (tabnabbing).
        httpResponse.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // Impede que outra origem carregue os recursos da API.
        httpResponse.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        // Isola os recursos embutidos.
        httpResponse.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        // Nao revela a URL completa da API para sites de terceiros.
        httpResponse.setHeader("Referrer-Policy", "no-referrer");
        // Bloqueia politicas antigas de navegador (plugins obsoletos).
        httpResponse.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        // Dados da API nao devem ficar em cache (podem conter dados pessoais).
        httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        // Nao expoe a versao do servidor.
        httpResponse.setHeader("X-Powered-By", "");

        chain.doFilter(request, response);
    }
}
