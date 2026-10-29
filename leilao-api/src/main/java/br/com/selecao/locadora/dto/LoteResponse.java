package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE SAIDA: LOTE
 * ============================================================================
 *
 *  Alem dos dados, devolve "totalLote" = quantidade * valorInicial.
 *  E o total de UM lote. Somando os totais de todos os lotes de um
 *  leilao, chega-se ao total do leilao (exercicio 7).
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoteResponse {

    /** Id do registro. */
    private Integer id;

    /** Numero do lote. */
    private Integer numeroLote;

    /** Descricao. */
    private String descricao;

    /** Quantidade. */
    private Integer quantidade;

    /** Valor inicial de cada item. */
    private BigDecimal valorInicial;

    /** Total deste lote = quantidade * valorInicial. */
    private BigDecimal totalLote;

    /** Descricao da unidade. */
    private String unidade;

    /** Id do leilao. */
    private Integer leilao;

    /** Momento da criacao. */
    private LocalDateTime createdAt;

    /** Momento da ultima alteracao. */
    private LocalDateTime updatedAt;
}
