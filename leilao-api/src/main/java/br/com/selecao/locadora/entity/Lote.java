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
 * ============================================================================
 *  ENTIDADE: LOTE
 * ============================================================================
 *
 *  REPRESENTA O QUE?
 *  O "item" dentro do leilao: o que esta sendo vendido, em que quantidade
 *  e por qual valor inicial.
 *
 *  POR QUE O TOTAL DO LEILAO E FEITO A PARTIR DO LOTE?
 *  (Exercicio 7 do enunciado)
 *  total do leilao = soma de (quantidade * valor_inicial) de todos os lotes.
 *  Exemplo: 5 lotes x 12.500,00 + 2 lotes x 8.750,00 = 80.000,00
 *
 *  POR QUE "BigDecimal" E NAO "double"?
 *  Porque dinheiro nao aceita erro de arredondamento. O tipo double
 *  (ponto flutuante) representa 0.1 de forma imprecisa, e varias somas
 *  desse tipo acumulam centavos errados. BigDecimal e aritmetica DECIMAL:
 *  0.1 + 0.2 = 0.3 exato. No banco, a coluna tambem e NUMERIC(14,2),
 *  nunca FLOAT ou DOUBLE PRECISION, pelo mesmo motivo.
 *
 *  MAPEAMENTO DIAGRAMA -> BANCO
 *  ------------------------------------------------------------------
 *  | Diagrama (logico) | Coluna no banco | Tipo     | Obrigatorio? |
 *  ------------------------------------------------------------------
 *  | id                | id               | INTEGER  | SIM (PK)     |
 *  | numeroLote        | numero_lote      | INTEGER  | nao          |
 *  | descricao         | descricao        | VARCHAR  | nao          |
 *  | quantidade        | quantidade       | INTEGER  | nao          |
 *  | valorInicial      | valor_inicial    | NUMERIC  | nao          |
 *  | unidade           | unidade          | VARCHAR  | nao          |
 *  | leilao            | leilao           | INTEGER  | SIM (*) (FK) |
 *  ------------------------------------------------------------------
 * ============================================================================
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
