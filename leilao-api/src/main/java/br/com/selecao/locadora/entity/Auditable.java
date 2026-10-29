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
 * ============================================================================
 *  CLASSE BASE DE AUDITORIA - "quem criou e quem mexeu por ultimo"
 * ============================================================================
 *
 *  O QUE E ISSO?
 *  Uma "superclasse" (MappedSuperclass) que guarda dois campos presentes em
 *  TODAS as tabelas do diagrama: created_at e updated_at. Como o diagrama
 *  repete esses dois campos nas 5 tabelas, repetir o codigo em cada entidade
 *  seria copiar e colar. Com heranca, escrevemos uma vez e todos heredam.
 *
 *  OBSERVACAO IMPORTANTE SOBRE O NOME "@MappedSuperclass":
 *  O Hibernate NAO cria uma tabela para esta classe. Ela e apenas um
 *  "molde" - as colunas created_at/updated_at sao criada dentro de cada
 *  tabela filha (tb_empresa, tb_leilao, ...). Por isso o nome: mapeada,
 *  mas sem tabela propria.
 *
 *  POR QUE IMPORTANTE DO PONTO DE VISTA DE SEGURANCA?
 *  Em qualquer sistema que lida com dados de terceiros (precos, prazos,
 *  DOCUMENTOS FISCAIS), e obrigatorio saber QUANDO o dado foi criado e QUEM
 *  alterou pela ultima vez. Isso e a base da "trilha de auditoria", exigida
 *  por normas como a LGPD e pela ISO 27001. Sem isso, nao ha como provar,
 *  depois do faito, o que mudou e quando.
 *
 *  COMO FUNCIONA:
 *  @PrePersist  -> roda ANTES de gravar um registro novo  (define created_at)
 *  @PreUpdate  -> roda ANTES de alterar um registro ja existente (atualiza
 *                 updated_at, sem mexer no created_at, que e imutavel)
 * ============================================================================
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
