package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE SAIDA: LEILAO
 * ============================================================================
 *
 *  Alem dos dados da tabela, devolve o ID do vendedor e a razao social
 *  dele. A razao social e conveniente para a tela, mas o id e o que
 *  realmente identifica a relacao (e e o que a tela usa para montar
 *  links de navegacao).
 * ============================================================================
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
