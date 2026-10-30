package br.com.selecao.locadora.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * Unidade: o local fisico onde o leilao acontece (galpao, filial).
 *
 * E a tabela mais simples do modelo - uma unica coluna alem do id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "tb_unidade")
@SequenceGenerator(name = "seq_unidade", sequenceName = "seq_unidade", allocationSize = 1)
public class Unidade extends Auditable {

    private static final long serialVersionUID = 1L;

    /** Chave primaria gerada pela sequencia seq_unidade. */
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq_unidade")
    @EqualsAndHashCode.Include
    private Integer id;

    /** Nome da unidade. Obrigatorio (asterisco no diagrama). */
    @Column(name = "nome", nullable = false, length = 128)
    private String nome;
}
