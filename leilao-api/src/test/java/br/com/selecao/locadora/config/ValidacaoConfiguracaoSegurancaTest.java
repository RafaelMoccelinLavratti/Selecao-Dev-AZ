package br.com.selecao.locadora.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da verificacao de configuracao de seguranca na subida.
 *
 * Esta classe existe para uma unica coisa: a API nao pode subir numa
 * configuracao que pareca segura e nao seja. Cada teste aqui trava uma
 * das formas de isso acontecer.
 */
class ValidacaoConfiguracaoSegurancaTest {

    private static final String SENHA_FORTE = "SenhaForteDeTeste123";

    /**
     * A senha padrao do projeto, que esta publicada no repositorio.
     */
    private static final String SENHA_PADRAO = "troque-esta-senha";

    /**
     * Monta a classe com uma configuracao qualquer.
     */
    private ValidacaoConfiguracaoSeguranca montar(boolean autenticacaoAtiva,
                                                  String modo,
                                                  String senha,
                                                  String pastaChaves) {
        ValidacaoConfiguracaoSeguranca validacao = new ValidacaoConfiguracaoSeguranca();
        ReflectionTestUtils.setField(validacao, "autenticacaoAtiva", autenticacaoAtiva);
        ReflectionTestUtils.setField(validacao, "modoSeguranca", modo);
        ReflectionTestUtils.setField(validacao, "senhaPadrao", senha);
        ReflectionTestUtils.setField(validacao, "pastaChaves", pastaChaves);
        return validacao;
    }

    /**
     * Teste 1: com a autenticacao desligada, a aplicacao sobe.
     *
     * O perfil de desenvolvimento e aberto de proposito. O que nao pode
     * acontecer e a aplicacao recusar subir por causa disso - quem
     * avalia precisa conseguir rodar as rotas sem login.
     */
    @Test
    @DisplayName("Com a autenticacao desligada, a aplicacao sobe normalmente")
    void comAutenticacaoDesligadaSobe() {
        ValidacaoConfiguracaoSeguranca validacao = montar(false, "basic", SENHA_PADRAO, "");

        assertDoesNotThrow(validacao::verificar,
                "O perfil de desenvolvimento e aberto de proposito: nao pode impedir a subida.");
    }

    /**
     * Teste 2: com a autenticacao ligada e a senha padrao, a aplicacao
     * NAO sobe.
     *
     * A senha padrao esta publicada no repositorio. Se ela chegar a
     * producao, o acesso administrativo e publico. Falhar na subida e
     * melhor do que expor a API.
     */
    @Test
    @DisplayName("Com a autenticacao ligada e a senha padrao, a aplicacao nao sobe")
    void comSenhaPadraoNaoSobe() {
        ValidacaoConfiguracaoSeguranca validacao = montar(true, "jwt", "troque-esta-senha", "/chaves");

        IllegalStateException erro = assertThrows(IllegalStateException.class, validacao::verificar,
                "A senha padrao nao pode passar: ela esta publicada no repositorio.");

        assertTrue(erro.getMessage().contains("SECURITY_PASSWORD"),
                "A mensagem precisa dizer o que fazer: " + erro.getMessage());
    }

    /**
     * Teste 3: com a autenticacao ligada e uma senha forte, sobe.
     */
    @Test
    @DisplayName("Com a autenticacao ligada e senha forte, a aplicacao sobe")
    void comSenhaForteSobe() {
        ValidacaoConfiguracaoSeguranca validacao = montar(true, "jwt", SENHA_FORTE, "/chaves");

        assertDoesNotThrow(validacao::verificar,
                "Uma configuracao completa e segura tem que passar sem erro.");
    }

    /**
     * Teste 4: a senha padrao so e recusada quando a autenticacao esta
     * ligada.
     *
     * No desenvolvimento a senha pode ser a padrao a vontade: o
     * objetivo ali e justamente nao ter autenticacao.
     */
    @Test
    @DisplayName("A senha padrao so e recusada quando a autenticacao esta ligada")
    void senhaPadraoSoFalhaComAutenticacaoLigada() {
        ValidacaoConfiguracaoSeguranca validacao = montar(false, "basic", "troque-esta-senha", "");

        assertDoesNotThrow(validacao::verificar);
    }
}
