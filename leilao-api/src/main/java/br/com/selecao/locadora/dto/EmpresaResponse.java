package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE SAIDA: EMPRESA  (o que a API devolve)
 * ============================================================================
 *
 *  OBSERVACAO DE SEGURANCA (a mais importante deste arquivo):
 *  NAO existe campo "senha" aqui. A senha - nem em texto puro, nem o
 *  hash - nunca sai da API. Isso se chama "nao expor credenciais na
 *  saida" e e regra basica de seguranca da informacao.
 *
 *  O "usuario" aparece, porque a tela precisa mostrar quem e a empresa,
 *  mas sem qualquer segredo associado.
 * ============================================================================
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
