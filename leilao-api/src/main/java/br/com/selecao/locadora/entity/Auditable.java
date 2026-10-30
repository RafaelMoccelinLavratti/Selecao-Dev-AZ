package br.com.selecao.locadora.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base das entidades: created_at e updated_at.
 *
 * As duas colunas existem em todas as tabelas do diagrama. O @PrePersist
 * preenche created_at na inclusao e o @PreUpdate atualiza updated_at na
 * alteracao.
 *
 * Registrar quem mexe e quando e o que permite responder, depois, o que foi
 * alterado em um dado.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class Auditable implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Momento em que o registro foi criado. Nunca muda depois disso.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento da ultima alteracao. Atualizado automaticamente a cada UPDATE.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Chamado pelo Hibernate automaticamente antes de um INSERT.
     * Preenche as duas datas com o agora.
     */
    @PrePersist
    public void aoInserir() {
        LocalDateTime agora = LocalDateTime.now();
        this.createdAt = agora;
        this.updatedAt = agora;
    }

    /**
     * Chamado pelo Hibernate automaticamente antes de um UPDATE.
     * Atualiza SOMENTE a updated_at - a created_at e a prova original de
     * quando o dado nasceu e por isso nao pode ser reescrita.
     */
    @PreUpdate
    public void aoAtualizar() {
        this.updatedAt = LocalDateTime.now();
    }
}
