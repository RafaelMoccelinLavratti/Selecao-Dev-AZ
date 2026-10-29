package br.com.selecao.locadora.exception;

/**
 * ============================================================================
 *  EXCECAO: REGISTRO NAO ENCONTRADO
 * ============================================================================
 *
 *  SITUACAO EM QUE ACONTECE:
 *  O cliente pediu GET /unidades/999 e nao existe nenhuma unidade com id 999.
 *
 *  POR QUE EXISTE UMA EXCECAO PROPRIA?
 *  Porque o controlador precisa transformar um "problema" em uma RESPOSTA
 *  HTTP clara. Se deixassemos o erro vazar como NullPointerException, o
 *  usuario receberia um 500 e, no logs, uma pilha de execucao - que em
 *  producao pode vazar detalhes internos da aplicacao (uma falha
 *  classique de seguranca da informacao).
 *
 *  O GlobalExceptionHandler converte esta excecao em HTTP 404 com uma
 *  mensagem util e SEM stack trace na resposta.
 * ============================================================================
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
