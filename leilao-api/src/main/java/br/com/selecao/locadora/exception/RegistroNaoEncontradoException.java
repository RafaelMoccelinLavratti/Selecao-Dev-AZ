package br.com.selecao.locadora.exception;

/**
 * Excecao de registro inexistente.
 *
 * Lancada quando o registro pedido nao existe (GET /unidades/999). O
 * GlobalExceptionHandler transforma em 404 com o corpo padronizado.
 */
public class RegistroNaoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param mensagem Texto que explica o que nao foi encontrado. E devolvido
     *                ao cliente (sem dados sensiveis, apenas o que ele pediu).
     */
    public RegistroNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
