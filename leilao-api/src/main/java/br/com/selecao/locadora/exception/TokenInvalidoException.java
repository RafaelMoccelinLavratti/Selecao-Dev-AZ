package br.com.selecao.locadora.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ============================================================================
 *  EXCECAO: TOKEN INVALIDO
 * ============================================================================
 *
 *  Lancada quando um JWT nao pode ser aceito: assinatura invalida, token
 *  expirado, emissor desconhecido, chave de assinatura nao reconhecida ou
 *  token malformado.
 *
 *  ------------------------------------------------------
 *  POR QUE EXISTE UMA EXCECAO PROPRIA?
 *  ------------------------------------------------------
 *  Porque "token invalido" e uma falha de AUTENTICACAO, e nao uma regra
 *  de negocio nem um erro interno. Cada uma dessas categorias tem um
 *  codigo HTTP e um significado diferentes:
 *
 *      - regra de negocio violada  -> 409 (conflito)
 *      - registro inexistente      -> 404 (nao encontrado)
 *      - token invalido            -> 401 (nao autorizado)
 *      - erro inesperado           -> 500 (erro interno)
 *
 *  Sem essa distincao, um token invalido acabaria respondendo 409 ou 500,
 *  e o cliente nao saberia que precisa se autenticar de novo.
 *
 *  ------------------------------------------------------
 *  A MENSAGEM E DELIBERADAMENTE GENERICA
 *  ------------------------------------------------------
 *  Nao dizemos QUAL parte do token falhou (expirou? assinatura? formato?).
 *  Essa informacao ajuda um atacante a ajustar o ataque - por exemplo,
 *  saber que o token expirou sohora o faz tentar um token de renovacao.
 *  O cliente recebe "Token invalido ou expirado", e o LOG do servidor
 *  guarda o detalhe.
 * ============================================================================
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Cria a excecao com o detalhe tecnico (que vai para o log) e a
     * mensagem enviada ao cliente (que nao diz nada de especifico).
     *
     * @param detalhe tecnico do erro, registrado apenas no log do servidor
     */
    public TokenInvalidoException(String detalhe) {
        super(detalhe);
    }
}
