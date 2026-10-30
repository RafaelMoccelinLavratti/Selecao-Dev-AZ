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
 * DTO de entrada do lote.
 *
 * E o que entra na API: por isso os campos sao validados aqui, com o
 * tamanho maximo igual ao da coluna no banco.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoteRequest {

    /**
     * Numero do lote dentro do leilao.
     *
     * Comeca em 1 (e nao em 0): o zero e o que o banco usaria para
     * "sem numero", e nao faz sentido um lote "de numero zero".
     *
     * @Min(1) impede que o valor seja zero ou negativo.
     */
    @Min(value = 1, message = "O numero do lote deve ser maior que zero.")
    private Integer numeroLote;

    /**
     * Descricao do item leiloado.
     *
     * @Size(max = 80) respeita o tamanho maximo da coluna no banco.
     * O limite aqui e o que impede o envio de um texto gigante que
     * nao entraria na coluna (e o banco recusaria com um erro feio).
     */
    @Size(max = 80, message = "A descricao deve ter no maximo 80 caracteres.")
    private String descricao;

    /**
     * Quantidade de itens no lote.
     *
     * @Min(1) - um lote com zero itens nao faz sentido em um leilao.
     */
    @Min(value = 1, message = "A quantidade deve ser maior que zero.")
    private Integer quantidade;

    /**
     * Valor inicial de cada item (em reais).
     *
     * BigDecimal, nunca double - dinheiro em ponto flutuante acumula
     * erro de arredondamento.
     *
     * @DecimalMin("0.0") garante que o preco nunca seja negativo.
     * Dinheiro negativo nao faz sentido em um leilao.
     */
    @DecimalMin(value = "0.0", message = "O valor inicial nao pode ser negativo.")
    private BigDecimal valorInicial;

    /**
     * Descricao da unidade de medida (ex.: "kg", "unidade", "m3").
     *
     * @Size(max = 128) respeita a coluna do banco.
     */
    @Size(max = 128, message = "A unidade deve ter no maximo 128 caracteres.")
    private String unidade;

    /**
     * Id do leilao ao qual o lote pertence.
     *
     * @NotNull - e obrigatorio (asterisco no diagrama).
     *
     * O "@NotNull" garante que o campo chegou preenchido. Ele NAO
     * garante que o leilao exista no banco - essa e uma REGRA DE
     * NEGOCIO, verificada na camada "business" (LoteBO), que lanca
     * RegraNegocioException com uma mensagem clara para o usuario.
     */
    @NotNull(message = "O leilao e obrigatorio.")
    private Integer leilao;
}
