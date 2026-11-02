package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracao de CORS.
 *
 * O frontend roda na 8080 e a API na 8081: sao origens diferentes, e o
 * navegador so aceita a chamada se a API autorizar explicitamente. A
 * lista de origens vem de app.cors.origins - nunca "*", porque "*" junto
 * de credenciais permitiria CSRF entre origens.
 *
 * IMPORTANTE - o CORS e' configurado em dois lugares, de proposito:
 *
 *   1. Aqui, no "addCorsMappings", que vale para as requisicoes que
 *      chegam no DispatcherServlet (os controllers).
 *
 *   2. No "FiltroCorsGlobal", que roda ANTES de qualquer filtro - e
 *      portanto antes do limite de requisicoes.
 *
 * Por que o segundo e' necessario: quando o filtro de rate limit barra
 * uma requisicao, ele responde 429 e NAO chega no DispatcherServlet.
 * Sem o filtro global, essa resposta sai sem o cabecalho
 * "Access-Control-Allow-Origin" - e o navegador, que so pode ler o
 * status quando o CORS autoriza, reporta "erro de CORS" em vez de
 * "429, muitas requisicoes". O usuario ve um erro de rede, quando o
 * que houve foi um limite de taxa.
 *
 * E o preflight (OPTIONS) suffers o mesmo problema: ele e' barrado
 * antes do CORS responder, e o navegador nunca descobre que o metodo
 * seria aceito. Pior: como toda requisicao com o cabecalho
 * "Authorization" gera um preflight, cada chamada do frontend gastaria
 * DUAS vagas do limite (uma no OPTIONS, outra no GET).
 *
 * O filtro global resolve os dois: ele responde o preflight com 200 e
 * devolve os cabecalhos de CORS em TODA resposta - inclusive nas
 * barradas por limite.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Lista de origens autorizadas, vindas da variavel de ambiente
     * "app.cors.origins" (separadas por virgula).
     */
    @Value("${app.cors.origins:http://localhost:8080,http://127.0.0.1:8080}")
    private String origensPermitidas;

    /**
     * Registra as regras de CORS do Spring MVC.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {

        String[] origens = origensPermitidas.split(",");

        registry.addMapping("/**")
                // Lista fechada de origens permitidas (nunca "*").
                .allowedOrigins(origens)
                // Cabecalhos que o frontend pode enviar.
                .allowedHeaders("Content-Type", "Authorization", "Accept", "Origin", "X-Requested-With")
                // Metodos HTTP permitidos.
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                // Permite o envio de cookies/sessao, se um dia houver login.
                .allowCredentials(true)
                // Quanto tempo o navegador pode guardar a liberacao (1 hora).
                .maxAge(3600);
    }
}
