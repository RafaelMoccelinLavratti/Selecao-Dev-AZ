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
 * ============================================================================
 *  ENTIDADE: TOKEN DE RENOVACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  PARA QUE SERVE ESTA TABELA?
 *  ------------------------------------------------------
 *  O token de renovacao e o que permite a um usuario continuar usando o
 *  sistema sem digitar a senha a cada 15 minutos. Por isso ele e o token
 *  MAIS VALIOSO do sistema: quem o tiver pode renovar o acesso por 7 dias
 *  seguidos.
 *
 *  Esta tabela e o registro de quais tokens de renovacao existiram, quem
 *  os recebeu, se ainda valem e se ja foram usados.
 *
 *  ------------------------------------------------------
 *  POR QUE GUARDAMOS O HASH E NAO O TOKEN?
 *  ------------------------------------------------------
 *  Porque o banco de dados e um lugar onde vazamento acontece. Se a
 *  coluna guardasse o token em texto puro, um vazamento da tabela daria
 *  acesso imediato a conta de todos os usuarios - e nao exigiria
 *  nenhuma quebra de senha.
 *
 *  Guardando apenas o SHA-256, o vazamento da tabela nao permite login
 *  nenhum: o atacante teria o hash, e nao o token. Quebrar o hash por
 *  forca bruta e impractical (o SHA-256 e rapido, mas o espaco de busca
 *  e astronomico).
 *
 *  E o mesmo raciocinio do armazenamento de senha: guardamos o hash,
 *  nunca o segredo.
 *
 *  ------------------------------------------------------
 *  A "FAMILIA" DE TOKENS E A DETECCAO DE REUSO
 *  ------------------------------------------------------
 *  Quando o usuario renova o acesso, o token antigo e MARCADO COMO USADO
 *  e um token novo e emitido. Todos os tokens de uma mesma sessao
 *  compartilham um identificador de FAMILIA.
 *
 *  Se um token ja usado for apresentado de novo, isso significa que
 *  alguem copiou um token de renovacao (o cenario classico de roubo de
 *  token). A resposta nao e "recusar este token": e INVALIDAR TODA A
 *  FAMILIA, obrigando o usuario a se autenticar de novo.
 *
 *  Por que invalidar a familia inteira? Porque no cenario de roubo, o
 *  atacante e o usuario legitimo estao com o MESMO token. So a revogacao
 *  completa resolve: os dois perdem o acesso, e o usuario legitimo
 *  percebe o problema porque precisa logar de novo.
 *
 *  Isso se chama "deteccao de reuso" e e a mesma tecnica usada por
 *  bibliotecas de OAuth 2.0 consolidate.
 * ============================================================================
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
     * familia. E o que permite revogar a sessão inteira de uma vez.
     */
    @Column(name = "familia", nullable = false, length = 36)
    private String familia;

    /**
     * O token ja foi utilizado em uma renovacao?
     *
     * Um token de renovacao e de USO UNICO: assim que ele troca o
     * token de acesso, ele e marcado como usado. presented-lo de novo e
     * a evidencia de reuso.
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
