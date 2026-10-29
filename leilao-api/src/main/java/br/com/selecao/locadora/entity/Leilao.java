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
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 *  ENTIDADE: LEILAO
 * ============================================================================
 *
 *  REPRESENTA O QUE?
 *  O "evento" de venda. Junta um VENDEDOR (uma empresa), um horario previsto
 *  de inicio, um codigo e varios LOTES.
 *
 *  RELACOES
 *  - vendedor : ManyToOne -> Empresa. A FK "vendedor" aponta para tb_empresa.
 *    ManyToOne quer dizer "muitos leiloes para uma empresa" (N para 1).
 *  - lotes    : OneToMany -> Lote. O lado "inverso" da relacao: e aqui que
 *    fica a lista dos lotes do leilao. O campo "mappedBy = leilao" diz ao
 *    Hibernate que quem manda nesta relacao e o Lote (ele tem a FK).
 *
 *  POR QUE "fetch = LAZY"?
 *  LAZY significa "so busca no banco se alguem realmente precisar". Ao
 *  listar 12 leiloes, o Hibernate executa 1 consulta para os leiloes e so
 *  busca os lotes se a tela pedir. Eager (buscar sempre) faria N+1 consultas
 *  e deixaria a aplicacao lenta. LAZY e o padrao correto.
 *
 *  MAPEAMENTO DIAGRAMA -> BANCO
 *  ------------------------------------------------------------------
 *  | Diagrama (logico) | Coluna no banco | Tipo     | Obrigatorio? |
 *  ------------------------------------------------------------------
 *  | id                | id               | INTEGER  | SIM (PK)     |
 *  | codigo            | codigo           | INTEGER  | SIM (*)      |
 *  | descricao         | descricao        | VARCHAR  | nao          |
 *  | vendedor          | vendedor         | INTEGER  | SIM (*) (FK) |
 *  | inicioPrevisto    | inicio_previsto  | TIMESTAMP| nao          |
 *  ------------------------------------------------------------------
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "tb_leilao")
@SequenceGenerator(name = "seq_leilao", sequenceName = "seq_leilao", allocationSize = 1)
public class Leilao extends Auditable {

    private static final long serialVersionUID = 1L;

    /** Chave primaria gerada pela sequencia seq_leilao. */
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq_leilao")
    @EqualsAndHashCode.Include
    private Integer id;

    /** Codigo do leilao. Obrigatorio (asterisco no diagrama). */
    @Column(name = "codigo", nullable = false)
    private Integer codigo;

    /** Descricao do leilao. */
    @Column(name = "descricao", length = 80)
    private String descricao;

    /**
     * Empresa que VENDE este leilao.
     * "optional = false" diz que a FK e obrigatoria (bate com o asterisco
     * do diagrama). "fetch = LAZY" carrega sob demanda.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor", nullable = false)
    private Empresa vendedor;

    /** Data e hora previstas para o inicio do leilao. */
    @Column(name = "inicio_previsto")
    private LocalDateTime inicioPrevisto;

    /**
     * Lotes que pertencem a este leilao (lado inverso da relacao).
     * A lista comeca vazia e e preenchida pelo proprio Hibernate.
     */
    @OneToMany(mappedBy = "leilao", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Lote> lotes = new ArrayList<>();
}
