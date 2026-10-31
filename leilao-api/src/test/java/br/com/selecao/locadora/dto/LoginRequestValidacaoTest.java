package br.com.selecao.locadora.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes das regras de validacao do login.
 *
 * Este arquivo existe por causa de um bug real.
 *
 * O "LoginRequest" exigia "@Size(min = 8)" na senha. Mas a massa de
 * dados usa a senha "Senha@1" - 7 caracteres. O resultado: o login
 * devolvia HTTP 400 ("A senha deve ter entre 8 e 128 caracteres") sem
 * nem chegar a conferir a senha com o hash. Nove das doze empresas da
 * massa de dados estavam inacessiveis pela API, e o README anunciava
 * credenciais que nao funcionavam.
 *
 * A regra de senha minima pertence ao CADASTRO ("EmpresaRequest"),
 * onde a senha e' escolhida. No login, a senha ja' existe: o servidor
 * compara com o hash guardado. E quem decide o formato aceito e' o
 * cadastro, nao o login.
 */
class LoginRequestValidacaoTest {

    /**
     * O validador. Montado uma vez, porque criar um e' caro.
     */
    private static final Validator VALIDADOR = criarValidador();

    /**
     * Cria o validador do Bean Validation.
     */
    private static Validator criarValidador() {
        ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory();
        return fabrica.getValidator();
    }

    /**
     * Monta um login valido.
     */
    private LoginRequest loginValido() {
        LoginRequest request = new LoginRequest();
        request.setUsuario("admin.aurora");
        request.setSenha("Senha@1");
        return request;
    }

    /**
     * Diz se o campo informado tem algum erro de validacao.
     */
    private boolean temErro(LoginRequest request, String campo) {
        Set<ConstraintViolation<LoginRequest>> erros = VALIDADOR.validate(request);
        return erros.stream().anyMatch(violacao -> violacao.getPropertyPath().toString().equals(campo));
    }

    /**
     * A senha da massa de dados tem que passar.
     *
     * E' o teste que documenta o bug: com o "@Size(min = 8)", este
     * login era recusado antes de a senha ser conferida.
     */
    @Test
    @DisplayName("A senha da massa de dados (7 caracteres) e' aceita no login")
    void senhaDaMassaDeDadosEaceita() {

        LoginRequest request = loginValido();

        assertFalse(temErro(request, "senha"),
                "A senha da massa de dados nao pode ser recusada na validacao do login. "
                        + "A regra de senha minima pertence ao cadastro, nao ao login.");
    }

    /**
     * Uma senha de verdade, curta, tambem passa no login.
     *
     * O login nao e' o lugar de aplicar politica de senha: e' o lugar de
     * conferir a senha. Quem cadastra a conta ja' passou pela regra
     * "@Size(min = 8)" do "EmpresaRequest".
     */
    @Test
    @DisplayName("Uma senha curta de verdade tambem passa no login")
    void senhaCurtaPassaNoLogin() {

        LoginRequest request = loginValido();
        request.setSenha("abc");

        assertFalse(temErro(request, "senha"),
                "O login nao deve aplicar a regra de senha minima: essa e' do cadastro.");
    }

    /**
     * O usuario e' obrigatorio.
     */
    @Test
    @DisplayName("O usuario e' obrigatorio")
    void usuarioEObrigatorio() {

        LoginRequest request = loginValido();
        request.setUsuario("  ");

        assertTrue(temErro(request, "usuario"),
                "Um usuario so com espacos deveria ser recusado.");
    }

    /**
     * A senha e' obrigatoria.
     */
    @Test
    @DisplayName("A senha e' obrigatoria")
    void senhaEObrigatoria() {

        LoginRequest request = loginValido();
        request.setSenha("");

        assertTrue(temErro(request, "senha"),
                "Uma senha vazia deveria ser recusada.");
    }

    /**
     * A senha nao pode ser um corpo de requisicao gigante.
     */
    @Test
    @DisplayName("A senha tem limite de tamanho")
    void senhaTemLimiteDeTamanho() {

        StringBuilder enorme = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            enorme.append("a");
        }

        LoginRequest request = loginValido();
        request.setSenha(enorme.toString());

        assertTrue(temErro(request, "senha"),
                "Uma senha de 200 caracteres deveria ser recusada.");
    }
}
