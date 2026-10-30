package br.com.selecao.locadora.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.math.BigDecimal;

/**
 * Lote: o item dentro do leilao.
 *
 * Carrega quantidade e valor inicial. O total do leilao (exercicio 7) e a
 * soma de quantidade x valor inicial de todos os lotes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "tb_lote")
@SequenceGenerator(name = "seq_lote", sequenceName = "seq_lote", allocationSize = 1)
public class Lote extends Auditable {

    private static final long serialVersionUID = 1L;

    /** Chave primaria gerada pela sequencia seq_lote. */
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq_lote")
    @EqualsAndHashCode.Include
    private Integer id;

    /** Numero sequencial do lote dentro do leilao. */
    @Column(name = "numero_lote")
    private Integer numeroLote;

    /** Descricao do item leiloado. */
    @Column(name = "descricao", length = 80)
    private String descricao;

    /** Quantidade de itens no lote. */
    @Column(name = "quantidade")
    private Integer quantidade;

    /** Valor inicial de CADA item do lote (em reais). */
    @Column(name = "valor_inicial", precision = 14, scale = 2)
    private BigDecimal valorInicial;

    /** Descricao da unidade (ex.: "Torno CNC serie 3000 usado"). */
    @Column(name = "unidade", length = 128)
    private String unidade;

    /**
     * Leilao ao qual este lote pertence.
     * "optional = false" -> FK obrigatoria (asterisco no diagrama).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leilao", nullable = false)
    private Leilao leilao;
}
