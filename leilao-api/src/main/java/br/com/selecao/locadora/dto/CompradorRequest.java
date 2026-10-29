package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * ============================================================================
 *  DTO DE ENTRADA: COMPRADOR
 * ============================================================================
 *
 *  Como a tabela tb_comprador tem chave primaria COMPOSTA (empresa, leilao),
 *  nao existe "id" unico. O DTO traz os dois ids que formam a chave.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompradorRequest {

    /** Id da empresa compradora. Obrigatorio. */
    @NotNull(message = "A empresa e obrigatoria.")
    private Integer empresa;

    /** Id do leilao comprado. Obrigatorio. */
    @NotNull(message = "O leilao e obrigatorio.")
    private Integer leilao;
}
