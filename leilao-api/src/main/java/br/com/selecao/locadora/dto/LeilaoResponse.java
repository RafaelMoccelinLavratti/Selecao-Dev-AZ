package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de saida do leilao.
 *
 * Alem dos dados da tabela, devolve o id do vendedor e a razao social dele:
 * o id e o que a tela precisa, a razao social e o que o usuario le.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeilaoResponse {

    /** Id do registro. */
    private Integer id;

    /** Codigo do leilao. */
    private Integer codigo;

    /** Descricao. */
    private String descricao;

    /** Id da empresa vendedora. */
    private Integer vendedor;

    /** Razao social da empresa vendedora. */
    private String razaoSocial;

    /** Inicio previsto. */
    private LocalDateTime inicioPrevisto;

    /** Momento da criacao. */
    private LocalDateTime createdAt;

    /** Momento da ultima alteracao. */
    private LocalDateTime updatedAt;
}
