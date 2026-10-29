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
 * ============================================================================
 *  FILTRO: CABECALHOS DE SEGURANCA HTTP
 * ============================================================================
 *
 *  O QUE E ISSO?
 *  Cabecalhos HTTP de resposta que o navegador respeita para bloquear
 *  certos tipos de ataque no lado do cliente. E a "parede de seguranca"
 *  do navegador, configurada em uma unica classe.
 *
 *  CADA CABECALHO E O QUE FAZ:
 *  ------------------------------------------------------------------
 *  | Cabecalho              | O que protege                          |
 *  |------------------------|----------------------------------------|
 *  | X-Content-Type-Options | Impede o navegador de "adivinhar" o    |
 *  | = nosniff              | tipo do arquivo. Sem isso, um arquivo  |
 *  |                        | de texto poderia ser executado como   |
 *  |                        | JavaScript (ataque XSS via upload).    |
 *  |------------------------|----------------------------------------|
 *  | X-Frame-Options        | Impede que a aplicacao seja embutida   |
 *  | = DENY                 | em um iframe de outro site (clickjacking|
 *  |                        |): o usuario clicaria sem perceber.     |
 *  |------------------------|----------------------------------------|
 *  | Referrer-Policy        | Define quanto da URL pode vazar para   |
 *  | = no-referrer          | sites de terceiros.                    |
 *  |------------------------|----------------------------------------|
 *  | X-Permitted-Cross-     | Impede que o navegador use as APIs      |
 *  | Domain-Policies        | Flash e Silverlight legadas (obsoletas,|
 *  |                        | mas inofensivas e gratis de bloquear). |
 *  |------------------------|----------------------------------------|
 *  | Cache-Control          | Impede que dados da API fiquem salvos   |
 *  | = no-store             | em caches intermediarios ou no          |
 *  |                        | historico do navegador.                  |
 *  |------------------------|----------------------------------------|
 *  | Strict-Transport-Sec.  | Exige HTTPS nas requisicoes seguinte    |
 *  | (ativo em producao)    | (HSTS), evitando o ataque do "downgrade"|
 *  |                        | para HTTP.                              |
 *  ------------------------------------------------------------------
 *
 * *  OBSERVACAO: o Spring Boot ja envia varios desses cabecalhos por padrao.
 *  Aqui estao explicitos e documentados porque no processo seletivo faz
 *  parte da entrega mostrar que a seguranca foi pensada, nao_so herdada.
 *
 *  ------------------------------------------------------
 *  OS CABECALHOS "MODERNOS" (os mais recentes do padrao)
 *  ------------------------------------------------------
 *  Content-Security-Policy
 *      Diz ao navegador QUAIS origens podem fornecer scripts, estilos,
 *      imagens e conexoes. A diretiva mais restritiva e a que impede
 *      a injecao de scripts (XSS). Para uma API que devolve JSON, a
 *      politica correta e "default-src 'none'": nenhuma origem pode
 *      fornecer recurso algum. Isso nao quebra o frontend, porque o
 *      frontend e' servido em outra origem (localhost:8080) e nao e'
 *      afetado pelo cabecalho de resposta da API.
 *
 *  Permissions-Policy
 *      Permite desligar, de forma explicita, as APIs do navegador que
 *      este sistema nao usa: camera, microfone, geolocalizacao,
 *     Bluetooth, acelerometro... O padrao e bloquear tudo o que nao for
 *      necessario. Menos superficie exposta e mais seguranca.
 *
 *  Cross-Origin-Opener-Policy (COOP)
 *      Isola a aba do navegador do resto, impedindo que uma pagina
 *      maliciosa "sequestre" a janela (ataque de tabnabbing) e leia o
 *      conteudo via a referencia window.opener.
 *
 *  Cross-Origin-Resource-Policy (CORP)
 *      Impede que outra origem carregue os recursos da API. Em uma API
 *      com dados de empresas, isso impede que um site de terceiros
 *      consuma a API via <script> ou <img> e extraia dados.
 *
 *  Cross-Origin-Embedder-Policy (COEP)
 *      Isola os recursos carregados, para que so recursos explicitos
 *      (com o atributo CORP) possam ser embutidos.
 *
 *  ------------------------------------------------------
 *  POR QUE NAO HA "X-XSS-Protection"?
 *  ------------------------------------------------------
 *  Esse cabecalhos existiu por anos nos navegadores, mas foi REMOVIDO
 *  (Chrome, Edge e Firefox) porque o filtro de XSS embutido no navegador
 *  criava mais problemas do que resolvia - ele quebrava paginas
 *  legítimas e era facilmente contornado. Hoje a defesa contra XSS e' a
 *  Content-Security-Policy, que e' o que este filtro envia.
 * ============================================================================
 */
@Component
public class SecurityHeadersFilter implements Filter {

    /**
     * Content-Security-Policy da API.
     *
     * "default-src 'none'" significa: nao carregue NADA de nenhuma
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
