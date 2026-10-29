package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * ============================================================================
 *  DTO: PEDIDO DE RENOVACAO DE TOKEN
 * ============================================================================
 *
 *  E o corpo do POST /auth/refresh.
 *
 *  ------------------------------------------------------
 *  O REQUEST E O RESPONSE SAO DIFERENTES DE PROPOSITO
 *  ------------------------------------------------------
 *  Este DTO tem um campo; a resposta tem cinco. Sao coisas diferentes:
 *  o pedido e "me de novo o acesso", a resposta e "aqui esta o acesso,
 *  a proxima renovacao e esta, e voce tem ate tal hora".
 *
 *  Manter os dois separados deixa o contrato claro e permite evoluir a
 *  resposta (adicionar um campo) sem quebrar quem envia o pedido.
 *
 *  ------------------------------------------------------
 *  O LIMITE DE TAMANHO
 *  ------------------------------------------------------
 *  O token de renovacao tem tamanho previsivel (algumas centenas de
 *  caracteres). Limitar o tamanho da entrada evita que um cliente envie
 *  megabytes para o endpoint, que faz uma operacao de hash (e, portanto,
 *  consome CPU) em cada tentativa.
 * ============================================================================
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
