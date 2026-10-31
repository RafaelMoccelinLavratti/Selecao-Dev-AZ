package br.com.selecao.locadora.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Sessao revogada (lista de bloqueio dos tokens de acesso).
 *
 * O token de acesso e um JWT stateless: o servidor nao guarda o estado
 * dele, so confere a assinatura e o prazo. E o que o torna barato de
 * verificar, mas tem um preco - no logout, o token continua valido ate
 * expirar.
 *
 * Esta tabela resolve esse preco. O token de acesso carrega o
 * identificador da familia de sessao (claim "fam"); quando a sessao e
 * revogada, a familia entra aqui, e qualquer token de acesso dela passa
 * a ser recusado.
 *
 * A chave e a familia, e nao o token, porque a revogacao e sempre em
 * lote: vale para todos os tokens que a renovacao gerou.
 *
 * "expiraEm" e' o instante em que o registro pode ser apagado. Nenhum
 * token da familia vale alem dele, entao o registro ja nao serve para
 * nada - e sem essa limpeza a tabela cresceria para sempre.
 */
@Getter
@Setter
@Entity
@Table(name = "tb_token_revogado", indexes = {
        @Index(name = "tb_token_revogado_expiracao_idx", columnList = "expira_em")
})
public class TokenRevogado implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identificador da familia de sessao revogada.
     *
     * E a chave primaria: a mesma familia nunca e registrada duas vezes.
     */
    @Id
    @Column(name = "chave", nullable = false, length = 36)
    private String chave;

    /**
     * Usuario dono da sessao revogada.
     *
     * Fica registrado para o log de auditoria saber de quem era a
     * sessao que foi encerrada.
     */
    @Column(name = "usuario", length = 100)
    private String usuario;

    /**
     * Instante em que a revogacao aconteceu.
     */
    @Column(name = "revogado_em", nullable = false)
    private LocalDateTime revogadoEm;

    /**
     * Instante em que este registro pode ser apagado.
     *
     * Nenhum token da familia vale alem deste ponto: todos os tokens de
     * acesso da sessao ja expiraram. A partir dai, guardar o registro
     * so ocupa espaco.
     */
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;
}
