package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de saida da listagem de leiloes (exercicio 7).
 *
 * Traz o que a tela precisa: dados do leilao, nome do vendedor e o total
 * calculado no banco. E um DTO e nao a entidade porque a entidade tem todos
 * os campos, e a API nao deve expor o que a tela nao usa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeilaoResumoDTO {

    /** Id do leilao. */
    private Integer id;

    /** Codigo do leilao. */
    private Integer codigo;

    /** Descricao do leilao. */
    private String descricao;

    /** Data e hora previstas para o inicio. */
    private LocalDateTime inicioPrevisto;

    /** Razao social da empresa vendedora (vem da relacao com tb_empresa). */
    private String razaoSocial;

    /** Total do leilao = soma de (quantidade * valorInicial) dos lotes. */
    private BigDecimal total;
}
