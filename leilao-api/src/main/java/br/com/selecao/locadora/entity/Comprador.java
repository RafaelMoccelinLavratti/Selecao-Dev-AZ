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
 * Vinculo entre empresa e leilao, no papel de compradora.
 *
 * E uma tabela de juncao: nao tem dados proprios, apenas os dois ids que
 * formam a chave primaria composta.
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
     * Chave primaria composta (empresa + leilao).
     * Gravar nela e o mesmo que gravar as duas colunas na tabela.
     */
    @EmbeddedId
    @EqualsAndHashCode.Include
    private CompradorId id;
}
