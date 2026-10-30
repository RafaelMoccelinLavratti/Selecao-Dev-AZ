package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * DTO de entrada do leilao.
 *
 * "vendedor" e o id da empresa vendedora, nao um objeto empresa inteiro: o
 * cliente informa qual empresa, e o backend busca o resto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeilaoRequest {

    /** Codigo do leilao. Obrigatorio (asterisco no diagrama). */
    @NotNull(message = "O codigo do leilao e obrigatorio.")
    private Integer codigo;

    /** Descricao. Tamanho maximo 80. */
    @Size(max = 80, message = "A descricao deve ter no maximo 80 caracteres.")
    private String descricao;

    /** Id da empresa vendedora. Obrigatorio (asterisco no diagrama). */
    @NotNull(message = "O vendedor e obrigatorio.")
    private Integer vendedor;

    /** Data e hora previstas para o inicio do leilao. */
    private LocalDateTime inicioPrevisto;
}
