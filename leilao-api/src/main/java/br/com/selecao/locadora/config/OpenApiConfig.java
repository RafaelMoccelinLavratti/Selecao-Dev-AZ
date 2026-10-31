package br.com.selecao.locadora.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao da documentacao OpenAPI da API.
 *
 * O springdoc gera a especificacao a partir do codigo: se uma rota
 * mudar, a documentacao muda junto. O que esta classe faz e descrever
 * o que o codigo nao consegue dizer sozinho - o nome da API, a versao,
 * o esquema de autenticacao.
 *
 * A documentacao fica em /swagger-ui.html e o arquivo OpenAPI em
 * /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Descreve a API e o esquema de autenticacao.
     *
     * O esquema declarado e' o "Bearer", ou seja, o cabecalho
     * "Authorization: Bearer <token>". Ele corresponde ao modo JWT, que
     * e o padrao do perfil de producao.
     *
     * @return a descricao da API
     */
    @Bean
    public OpenAPI descricaoDaApi() {

        return new OpenAPI()
                .info(new Info()
                        .title("API de Leiloes")
                        .version("1.0.0")
                        .description("Consulta e gestao de leiloes, unidades, empresas, "
                                + "lotes e vinculos de comprador. "
                                + "A documentacao e gerada a partir do codigo.")
                        .license(new License().name("Projeto de avaliacao - AZ Tecnologia")))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT obtido em POST /auth/login. "
                                        + "Envie no cabecalho Authorization.")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
