package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 *  DTO: RESPOSTA DE AUTENTICACAO
 * ============================================================================
 *
 *  E o corpo devolvido pelo POST /auth/login e pelo POST /auth/refresh.
 *
 *  ------------------------------------------------------
 *  OS CAMPOS E POR QUE CADA UM EXISTE
 *  ------------------------------------------------------
 *  - tokenAcesso: o token enviado em todas as requisicoes, no cabecalho
 *    "Authorization: Bearer <token>". Vida curta (15 minutos).
 *
 *  - tokenRenovacao: o token usado para pedir um novo token de acesso.
 *    Vida longa (7 dias). Vai para o POST /auth/refresh.
 *
 *  - tipoToken: a palavra "Bearer". O padrao OAuth 2.0 (RFC 6750)
 *    exige que o cliente declare o tipo do token, para que o servidor
 *    possa aceitar outros esquemas no futuro sem ambiguidade.
 *
 *  - expiraEmSegundos: o tempo restante do token de acesso. O cliente
 *    usa isso para renovar ANTES de o token expirar, e nao descobrir a
 *    expiracao quando a proxima requisicao falha.
 *
 *  - usuario: quem foi autenticado. E a unica informacao sobre a pessoa
 *    que o servidor devolve - o suficiente para a interface mostrar quem
 *    esta usando, sem vazar dado sensivel.
 *
 *  ------------------------------------------------------
 *  POR QUE A SENHA NAO APARECE AQUI
 *  ------------------------------------------------------
 *  Nao ha campo de senha nesta resposta, e nem o hash. A autenticacao
 *  serve para entregar um TOKEN, nunca para devolver a credencial.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Token de acesso (curta duracao).
     */
    private String tokenAcesso;

    /**
     * Token de renovacao (longa duracao).
     */
    private String tokenRenovacao;

    /**
     * Tipo do token, sempre "Bearer".
     */
    private String tipoToken;

    /**
     * Segundos ate a expiracao do token de acesso.
     */
    private long expiraEmSegundos;

    /**
     * Usuario autenticado.
     */
    private String usuario;
}
