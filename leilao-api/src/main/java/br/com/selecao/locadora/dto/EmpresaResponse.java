package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Empresa na forma de saida da API.
 *
 * Repare que nao existe campo "senha" aqui. A senha - nem em texto puro,
 * nem o hash - nunca sai da API. O "usuario" aparece, porque a tela
 * precisa mostrar quem e a empresa, mas sem nenhum segredo associado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaResponse {

    /** Id do registro. */
    private Integer id;

    /** Razao social. */
    private String razaoSocial;

    /** CNPJ (sempre normalizado, so digitos). */
    private String cnpj;

    /** Logradouro. */
    private String logadouro;

    /** Municipio. */
    private String municipio;

    /** Numero. */
    private String numero;

    /** Complemento. */
    private String complemento;

    /** Quantidade. */
    private Integer quantidade;

    /** CEP. */
    private String cep;

    /** Telefone. */
    private String telefone;

    /** E-mail. */
    private String email;

    /** Site. */
    private String site;

    /** Nome de usuario (sem senha). */
    private String usuario;

    /** Momento da criacao. */
    private LocalDateTime createdAt;

    /** Momento da ultima alteracao. */
    private LocalDateTime updatedAt;
}
