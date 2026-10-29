package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ============================================================================
 *  CONFIGURACAO: CORS (Cross-Origin Resource Sharing)
 * ============================================================================
 *
 *  O PROBLEMA QUE ESTA CONFIGURACAO RESOLVE?
 *  O frontend (Vue) roda na porta 8080 e a API (Spring Boot) na 8081.
 *  Sao ORIGENS DIFERENTES (host:porta diferentes). O navegador, por
 *  seguranca (a "politica de origem mesma" / Same-Origin Policy), bloqueia
 *  por padrao o JavaScript de uma pagina em 8080 de fazer requisicoes a
 *  8081. Sem CORS, a tela funciona no Postman mas nao funciona no navegador
 *  - e o erro aparece no console como "CORS policy: No 'Access-Control-
 *  Allow-Origin' header is present".
 *
 *  CORS e o MECANISMO OFICIAL do navegador para autorizar essa troca.
 *  O servidor responde com cabecalhos dizendo "eu aceito requisicoes
 *  vindas de TAL origem".
 *
 *  -------------------------------------------------------
 *  PONTO DE SEGURANCA (importante!)
 *  -------------------------------------------------------
 *  Existe um padrao perigoso muito comum:
 *
 *      addAllowedOrigin("*")  +  allowCredentials(true)
 *
 *  Isso liberaria QUALQUER site da internet para fazer requisicoes
 *  autenticadas na nossa API - o chamado ataque CSRF cross-origin.
 *
 *  Aqui a lista de origens e EXPLICITA e vem de variavel de ambiente
 *  (app.cors.origins). Em desenvolvimento liberamos o servidor do Vue
 *  (localhost:8080). Em producao, quem implanta define exatamente quais
 *  dominios podem chamar a API - e o padrao seguro (sem nenhuma origem
 *  liberada) continua valendo se a variavel nao for definida.
 * ============================================================================
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
