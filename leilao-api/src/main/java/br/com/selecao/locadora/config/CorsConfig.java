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
