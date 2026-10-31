package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corpo devolvido pelo login e pela renovacao.
 *
 * Devolve o par de tokens, o tipo do esquema, o tempo de vida do token de
 * acesso e o nome do usuario. Nao ha campo de senha aqui - nem em texto
 * puro, nem o hash: a autenticacao entrega um token, nunca devolve a
 * credencial.
 *
 * Os campos "papel", "empresaId" e "administrador" sao o vinculo com a
 * empresa. Eles vao aqui, alem do token, para que o frontend nao
 * precise decodificar o JWT para saber o que a pessoa pode fazer - o
 * token continua sendo a autoridade, mas a interface mostra o botao
 * certo sem adivinhar.
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

    /**
     * Papel do usuario (ROLE_ADMIN ou ROLE_EMPRESA).
     */
    private String papel;

    /**
     * Id da empresa do usuario. Nulo para o administrador.
     */
    private Integer empresaId;

    /**
     * O usuario e' o administrador da API?
     */
    private boolean administrador;
}
