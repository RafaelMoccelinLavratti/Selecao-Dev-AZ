package br.com.selecao.locadora.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Token de renovacao.
 *
 * Guarda apenas o hash do token, nunca o token em si: se o banco vazar, o
 * token nao serve para nada.
 *
 * Os tokens de uma mesma sessao compartilham a "familia". Quando um token
 * ja usado volta a ser apresentado, a deteccao de reuso revoga a familia
 * inteira.
 */
@Getter
@Setter
@Entity
@Table(name = "tb_refresh_token", indexes = {
        @Index(name = "tb_refresh_token_hash_idx", columnList = "token_hash"),
        @Index(name = "tb_refresh_token_familia_idx", columnList = "familia")
})
public class RefreshToken implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identificador interno do registro.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_refresh_token")
    @SequenceGenerator(name = "seq_refresh_token", sequenceName = "seq_refresh_token", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    /**
     * SHA-256 do token de renovacao, em hexadecimal.
     *
     * Nunca guardamos o token em si - apenas o hash. Ver a explicacao no
     * cabecalho desta classe.
     */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /**
     * Usuario dono do token.
     */
    @Column(name = "usuario", nullable = false, length = 100)
    private String usuario;

    /**
     * Identificador da familia de tokens.
     *
     * Todos os tokens derivados da mesma autenticacao compartilham a
     * familia. E o que permite revogar a sessao inteira de uma vez.
     */
    @Column(name = "familia", nullable = false, length = 36)
    private String familia;

    /**
     * O token ja foi utilizado em uma renovacao?
     *
     * Um token de renovacao e de uso unico: assim que ele troca o token
     * de acesso, ele e marcado como usado. Apresenta-lo de novo e a
     * evidencia de reuso.
     */
    @Column(name = "utilizado", nullable = false)
    private Boolean utilizado;

    /**
     * Data de revogacao (renovacao concluida, logout ou reuso detectado).
     * Nulo enquanto o token estiver valido.
     */
    @Column(name = "data_revogacao")
    private LocalDateTime dataRevogacao;

    /**
     * IP de onde o token foi emitido. Ajuda a detectar um roubo: se o
     * token foi emitido de um IP e renovado de outro, e um forte sinal.
     */
    @Column(name = "ip_origem", length = 64)
    private String ipOrigem;

    /**
     * Momento da emissao.
     */
    @Column(name = "data_emissao", nullable = false)
    private LocalDateTime dataEmissao;

    /**
     * Momento em que o token deixa de valer.
     */
    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;
}
