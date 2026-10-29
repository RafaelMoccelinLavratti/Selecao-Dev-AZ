package br.com.selecao.locadora.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * ============================================================================
 *  ENTIDADE: COMPRADOR
 * ============================================================================
 *
 *  REPRESENTA O QUE?
 *  A participacao de uma empresa em um leilao como COMPRADORA.
 *  E uma tabela de juncao (tabela associativa): nao tem dados proprios,
 *  so liga "empresa" e "leilao".
 *
 *  POR QUE USAR @EmbeddedId E NAO @IdClass?
 *  - @IdClass : as duas colunas de FK ficam DENTRO da propria entidade.
 *    Funciona, mas deixa a entidade com dois campos "soltos".
 *  - @EmbeddedId : as duas colunas ficam dentro de uma classe separada
 *    (CompradorId), e a entidade tem UM campo so: o "id" composto.
 *    Fica mais legivel e permite passar o par (empresa, leilao) como
 *    parametro unico nos metodos do repositorio.
 *
 *  MAPEAMENTO DIAGRAMA -> BANCO
 *  ------------------------------------------------------------------
 *  | Diagrama (logico) | Coluna no banco | Tipo    | Obrigatorio? |
 *  ------------------------------------------------------------------
 *  | PK empresa         | empresa         | INTEGER | SIM (*)      |
 *  | PK leilao          | leilao          | INTEGER | SIM (*)      |
 *  ------------------------------------------------------------------
 *  Repare que as DUAS colunas formam a chave primaria (PK composta).
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "tb_comprador")
public class Comprador extends Auditable {

    private static final long serialVersionUID = 1L;

    /**
     * Chave primaria COMPOSTA (empresa + leilao).
     * Gravar nela e o mesmo que gravar as duas colunas na tabela.
     */
    @EmbeddedId
    @EqualsAndHashCode.Include
    private CompradorId id;
}
