package br.com.selecao.locadora.exception;

/**
 * Excecao de acesso negado (HTTP 403).
 *
 * Diferente do 401 (nao autenticado), aqui a pessoa esta autenticada e
 * tem identidade - mas nao pode tocar no recurso pedido. E o que
 * acontece quando uma empresa tenta ver os leiloes de outra, ou o
 * historico de auditoria, que e do administrador.
 *
 * A mensagem e explicita, mas nao entrega informacao que o usuario nao
 * devia ter: ela diz o que foi negado, sem dizer se o recurso existe.
 * "Voce nao tem permissao para este recurso" vale tanto para um
 * recurso que existe quanto para um que nao existe.
 */
public class AcessoNegadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param mensagem Explicacao clara da negacao, em portugues.
     */
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
