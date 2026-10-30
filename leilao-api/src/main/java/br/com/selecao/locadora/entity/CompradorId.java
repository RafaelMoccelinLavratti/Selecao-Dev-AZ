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
 * Chave primaria composta da tabela comprador.
 *
 * A tabela nao tem coluna id: a identidade de um comprador e o par
 * (empresa, leilao). Isso faz sentido no dominio - o mesmo par nao pode
 * se repetir, e a primary key composta e o que garante isso no banco.
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
