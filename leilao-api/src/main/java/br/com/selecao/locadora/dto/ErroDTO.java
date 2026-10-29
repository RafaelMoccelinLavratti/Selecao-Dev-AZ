package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE ERRO (formato unico de resposta de erro da API)
 * ============================================================================
 *
 *  Por que um padrao unico de erro?
 *  O frontend (e qualquer outro consumidor) passa a ter UMA regra para
 *  ler erros, em vez de cada endpoint ter um formato diferente. E mais
 *  importante: o corpo NUNCA contem stack trace, SQL ou nomes de classes.
 *  O campo "mensagem" e a unica informacao sobre o erro que sai da
 *  aplicacao - e ela e escrita para ser lida por uma pessoa.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErroDTO {

    /** Momento da geracao do erro. */
    private LocalDateTime timestamp;

    /** Codigo HTTP (400, 404, 409, 500...). */
    private Integer status;

    /** Tipo do erro, em palavras. */
    private String erro;

    /** Descricao do que aconteceu, em linguagem simples. */
    private String mensagem;

    /** Lista de campos invalidos (apenas quando ha validacao de entrada). */
    private java.util.Map<String, String> campos;
}
