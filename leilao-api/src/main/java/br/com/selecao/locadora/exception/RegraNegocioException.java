package br.com.selecao.locadora.exception;

/**
 * Excecao de regra de negocio violada.
 *
 * Exemplos: cadastrar empresa com CNPJ repetido, excluir um leilao que
 * ainda tem lotes. A regra e verificada antes de gravar, entao o banco
 * nunca chega a recusar a operacao e o usuario recebe uma mensagem util.
 */
public class RegraNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param mensagem Explicacao clara da regra quebrada, em portugues.
     */
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
