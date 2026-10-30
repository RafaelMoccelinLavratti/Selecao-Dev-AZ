package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Corpo do POST /auth/refresh.
 *
 * Tem um campo, a resposta tem cinco: o pedido e "me de novo o acesso", a
 * resposta e "aqui esta o acesso".
 *
 * O tamanho e limitado porque o token tem tamanho previsivel - um limite de
 * entrada evita que o cliente envie qualquer coisa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    /**
     * Token de renovacao a ser trocado por um novo par de tokens.
     */
    @NotBlank(message = "O token de renovacao e obrigatorio.")
    @Size(max = 4096, message = "O token de renovacao e invalido.")
    private String tokenRenovacao;
}
