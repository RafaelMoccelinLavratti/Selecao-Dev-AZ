package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE ENTRADA: UNIDADE  (POST /unidades e PUT /unidades/:id)
 * ============================================================================
 *
 *  PARA QUE SERVE UM DTO DE ENTRADA SEPARADO DA ENTIDADE?
 *  porque o que chega da internet (o "body" da requisicao) e DADO NAO
 *  CONFIAVEL. Se usassemos a entidade direto, um cliente poderia mandar
 *  campos que nao deviam ser enviados por fora - por exemplo o "id" (para
 *  trocar a chave de um registro) ou o "created_at" (para falsificar a
 *  trilha de auditoria).
 *
 *  Usando um DTO com apenas os campos permitidos, o sistema controla
 *  exatamente o que entra. Esse e o principio "whitelist" (lista de
 *  permissao) em seguranca de aplicacoes.
 *
 *  AS ANOTACOES DE VALIDACAO (@NotBlank, @Size...) sao o "Bean Validation",
 *  que faz parte do padrao Java (Jakarta). O Spring Boot 2.3+ removeu a
 *  validacao do starter-web, entao adicionamos a dependencia
 *  "spring-boot-starter-validation" no pom.xml de proposito.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeRequest {

    /**
     * Nome da unidade. Obrigatorio (asterisco no diagrama) e com no
     * maximo de 128 caracteres (tamanho da coluna no banco).
     */
    @NotBlank(message = "O nome da unidade e obrigatorio.")
    @Size(max = 128, message = "O nome da unidade deve ter no maximo 128 caracteres.")
    private String nome;
}
