package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * ============================================================================
 *  DTO DE ENTRADA: LOTE
 * ============================================================================
 *
 *  O "leilao" e o ID do leilao ao qual o lote pertence.
 *  "valorInicial" usa BigDecimal com @DecimalMin(value = "0.0") para
 *  garantir que o preco nunca seja negativo - dinheiro negativo nao faz
 *  sentido em um leilao.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoteRequest {

    /** Numero do lote dentro do leilao. */
    @Min(value = 1, message = "O numero do lote deve ser maior que zero.")
    private Integer numeroLote;

    /** Descricao do item. Tamanho maximo 80. */
    @Size(max = 80, message = "A descricao deve ter no maximo 80 caracteres.")
    private String descricao;

    /** Quantidade de itens no lote. */
    @Min(value = 1, message = "A quantidade deve ser maior que zero.")
    private Integer quantidade;

    /** Valor inicial de cada item. Nao pode ser negativo. */
    @DecimalMin(value = "0.0", message = "O valor inicial nao pode ser negativo.")
    private BigDecimal valorInicial;

    /** Descricao da unidade. Tamanho maximo 128. */
    @Size(max = 128, message = "A unidade deve ter no maximo 128 caracteres.")
    private String unidade;

    /** Id do leilao. Obrigatorio (asterisco no diagrama). */
    @NotNull(message = "O leilao e obrigatorio.")
    private Integer leilao;
}
