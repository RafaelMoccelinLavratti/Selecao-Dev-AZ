package br.com.selecao.locadora.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excecao de token recusado.
 *
 * Lancada quando o JWT nao pode ser aceito: assinatura invalida, expirado,
 * emissor desconhecido ou chave nao reconhecida. A mensagem e
 * deliberadamente generica - dizer qual parte falhou ajudaria quem esta
 * tentando adivinhar.
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
