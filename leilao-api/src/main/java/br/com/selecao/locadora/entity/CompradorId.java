package br.com.selecao.locadora.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * ============================================================================
 *  CHAVE COMPOSTA DA TABELA COMPRADOR
 * ============================================================================
 *
 *  O PROBLEMA QUE ESTA CLASSE RESOLVE?
 *  A tabela tb_comprador NAO tem coluna "id". A chave primaria dela e
 *  COMPOSTA: (empresa, leilao). Ou seja, a identidade de um comprador nao
 *  vem de um numero sequencial, e sim do PAR "empresa + leilao".
 *
 *  POR QUE ISSO FAZ SENTIDO NO DOMINIO?
 *  Porque um comprador participa de um leilao especifico. O mesmo par
 *  (empresa X no leilao Y) nao pode se repetir - e isso que a PRIMARY KEY
 *  composta garante no banco. Alem disso, nao faz sentido a mesma empresa
 *  comprar o mesmo leilao duas vezes.
 *
 *  O QUE E "@Embeddable"?
 *  Diz ao Hibernate: "esta classe nao e uma tabela; ela e um pedaco de
 *  chave que sera embutido dentro de outra entidade". together com
 *  @EmbeddedId na entidade Comprador, o Hibernate sabe usar
 *  (empresa, leilao) como identificador unico do registro.
 *
 *  POR QUE OS CAMPOS SAO "Integer" E NAO "@ManyToOne"?
 *  Deliberado. O diagrama define a tabela como uma JUNCAO pura (apenas
 *  duas chaves estrangeiras e as datas). Guardar o ID direto deixa o
 *  mapeamento exatamente igual ao desenho, evita proxies lazy desnecessarios
 *  e torna a chave composta trivial de entender.
 * ============================================================================
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompradorId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Id da empresa compradora. */
    @Column(name = "empresa", nullable = false)
    private Integer empresa;

    /** Id do leilao comprado. */
    @Column(name = "leilao", nullable = false)
    private Integer leilao;
}
