package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE SAIDA: COMPRADOR
 * ============================================================================
 *
 *  Alem dos ids, traz a razao social da empresa e o codigo do leilao, para
 *  que a tela possa exibir algo legivel sem precisar de outras consultas.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompradorResponse {

    /** Id da empresa compradora. */
    private Integer empresa;

    /** Razao social da empresa compradora. */
    private String razaoSocial;

    /** Id do leilao comprado. */
    private Integer leilao;

    /** Codigo do leilao comprado. */
    private Integer codigoLeilao;

    /** Momento da criacao. */
    private LocalDateTime createdAt;

    /** Momento da ultima alteracao. */
    private LocalDateTime updatedAt;
}
