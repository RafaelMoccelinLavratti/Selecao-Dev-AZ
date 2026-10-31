package br.com.selecao.locadora.config;

import java.time.Instant;

/**
 * Uma contagem: quantas vezes algo aconteceu, e ate quando vale.
 *
 * E o retorno dos contadores de seguranca (tentativas de login e
 * requisicoes por IP). O prazo viaja junto com o numero porque o
 * cabecalho "Retry-After" precisa dizer quanto tempo falta para a
 * janela abrir.
 */
public class Contagem {

    /**
     * Quantidade registrada.
     */
    private final int quantidade;

    /**
     * Instante em que a janela expira.
     */
    private final Instant expiraEm;

    /**
     * Cria uma contagem.
     *
     * @param quantidade numero de ocorrencias
     * @param expiraEm   instante em que a janela expira
     */
    public Contagem(int quantidade, Instant expiraEm) {
        this.quantidade = quantidade;
        this.expiraEm = expiraEm;
    }

    /**
     * Cria a contagem de um registro inexistente.
     *
     * @param agora instante de referencia
     * @return uma contagem zerada, que expira agora
     */
    public static Contagem vazia(Instant agora) {
        return new Contagem(0, agora);
    }

    /**
     * A quantidade registrada.
     *
     * @return o numero de ocorrencias
     */
    public int getQuantidade() {
        return quantidade;
    }

    /**
     * Quando a janela expira.
     *
     * @return o instante de expiracao
     */
    public Instant getExpiraEm() {
        return expiraEm;
    }
}
