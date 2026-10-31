package br.com.selecao.locadora.business;

/**
 * Papeis de acesso.
 *
 * O projeto tem dois papeis, e a distincao entre eles e o que impede
 * que uma empresa veja os dados de outra.
 *
 * ADMIN: o administrador da API. Vê tudo e escreve em tudo. Vem da
 * configuracao (app.security.username), e nao do banco.
 *
 * EMPRESA: o usuario cadastrado por uma empresa. Vê apenas o que
 * pertence a sua empresa - os leiloes que ela vende, os leiloes em que
 * ela esta inscrita como compradora, e os proprios dados. Vem da tabela
 * tb_empresa (colunas "usuario" e "senha").
 *
 * O prefixo "ROLE_" e o que o Spring Security espera: e ele que faz o
 * nome virar uma autoridade, usavel em hasRole(...) e em @PreAuthorize.
 */
public final class Papeis {

    /**
     * Administrador: acesso total.
     */
    public static final String ADMIN = "ROLE_ADMIN";

    /**
     * Usuario de uma empresa: acesso restrito aos proprios dados.
     */
    public static final String EMPRESA = "ROLE_EMPRESA";

    /**
     * Classe utilitária: nao pode ser instanciada.
     */
    private Papeis() {
        throw new IllegalStateException("Classe utilitária: não deve ser instanciada.");
    }
}
