package br.com.selecao.locadora.exception;

/**
 * ============================================================================
 *  EXCECAO: REGRA DE NEGOCIO VIOLADA
 * ============================================================================
 *
 *  SITUACOES EM QUE ACONTECE (exemplos reais deste projeto):
 *  - Tentar cadastrar uma empresa com um CNPJ que ja existe.
 *  - Tentar cadastrar um leilao apontando para uma empresa inexistente.
 *  - Tentar vincular um comprador a um leilao que ele ja comprou.
 *  - Tentar apagar uma empresa que ainda tem leiloes (a FK protege).
 *
 *  A DIFFERENCA PARA O "RegistroNaoEncontrado":
 *  - RegistroNaoEncontrado -> HTTP 404 (o recurso pedido nao existe)
 *  - RegraNegocio        -> HTTP 409 (o recurso existe, mas a operacao
 *                           viola uma regra do negocio)
 *
 *  ESTA EXCECAO E O "PORTAO DE SEGURANCA" DA CAMADA DE NEGOCIO:
 *  a regra e verificada ANTES de gravar, entao o banco nunca chega a
 *  recusar a operacao. O usuario recebe uma mensagem util em vez de um
 *  erro tecnico de banco de dados.
 * ============================================================================
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
