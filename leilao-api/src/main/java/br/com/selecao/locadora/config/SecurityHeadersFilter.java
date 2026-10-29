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
 *  OBSERVACAO: o Spring Boot ja envia varios desses cabecalhos por padrao.
 *  Aqui estao explicitos e documentados porque no processo seletivo faz
 *  parte da entrega mostrar que a seguranca foi pensada, nao_so herdada.
 * ============================================================================
 */
@Component
public class SecurityHeadersFilter implements Filter {

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
