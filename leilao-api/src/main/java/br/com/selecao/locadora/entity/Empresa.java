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
 * Empresa que participa dos leiloes.
 *
 * Pode ser vendedora (nos leiloes) e compradora (na tabela de vinculo). A
 * coluna "senha" guarda um hash, nunca a senha digitada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "tb_empresa")
@SequenceGenerator(name = "seq_empresa", sequenceName = "seq_empresa", allocationSize = 1)
public class Empresa extends Auditable {

    private static final long serialVersionUID = 1L;

    /**
     * Chave primaria. O valor NAO e enviado pelo cliente: sai da sequencia
     * do banco (nextval). Isso evita duplicidade e torna o insert seguro
     * quando varias pessoas cadastram ao mesmo tempo.
     */
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq_empresa")
    @EqualsAndHashCode.Include
    private Integer id;

    /** Razao social da empresa. Obrigatoria (asterisco no diagrama). */
    @Column(name = "razao_social", nullable = false, length = 84)
    private String razaoSocial;

    /** CNPJ com 14 digitos. Obrigatorio e unico (sublinhado no diagrama). */
    @Column(name = "cnpj", nullable = false, length = 32, unique = true)
    private String cnpj;

    /** Endereco - logradouro. */
    @Column(name = "logadouro", length = 84)
    private String logadouro;

    /** Endereco - municipio. */
    @Column(name = "municipio", length = 64)
    private String municipio;

    /** Endereco - numero. */
    @Column(name = "numero", length = 10)
    private String numero;

    /** Endereco - complemento. */
    @Column(name = "complemento", length = 84)
    private String complemento;

    /** Quantidade (campo do diagrama). */
    @Column(name = "quantidade")
    private Integer quantidade;

    /** CEP. */
    @Column(name = "cep", length = 16)
    private String cep;

    /** Telefone de contato. */
    @Column(name = "telefone", length = 32)
    private String telefone;

    /** E-mail de contato. */
    @Column(name = "email", length = 254)
    private String email;

    /** Site. */
    @Column(name = "site", length = 254)
    private String site;

    /** Nome de usuario para login. Unico (sublinhado no diagrama). */
    @Column(name = "usuario", length = 20, unique = true)
    private String usuario;

    /**
     * Hash da senha - nunca a senha em texto puro.
     * O tamanho de 128 caracteres comporta o hash do Argon2id.
     */
    @Column(name = "senha", length = 128)
    private String senha;
}
