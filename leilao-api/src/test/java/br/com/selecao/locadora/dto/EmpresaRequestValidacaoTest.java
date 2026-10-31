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
 * Testes das regras de validacao do cadastro de empresa.
 *
 * Aqui mora a regra que decide o que a API aceita. Um erro aqui nao
 * quebra a aplicacao - ela simplesmente recusa (ou aceita) dados que
 * deveria recusar (ou aceitar).
 *
 * O teste mais importante deste arquivo e' o do telefone: ele grava o
 * bug que existia no projeto. O regex antigo exigia o DDD entre
 * parenteses, e a propria massa de dados era rejeitada por ele -
 * qualquer PUT ou POST de empresa devolvia HTTP 400, porque os
 * telefones da massa estao em "1130001100" (10 digitos, sem
 * parenteses).
 *
 * A mensagem de erro tambem importa: ela diz o formato aceito. Um
 * "Use (00) 0000-0000" que rejeita "1130001100" engana o usuario.
 */
class EmpresaRequestValidacaoTest {

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
     * Monta uma empresa valida, para o teste mexer so num campo.
     */
    private EmpresaRequest empresaValida() {
        EmpresaRequest request = new EmpresaRequest();
        request.setRazaoSocial("Metalurgica Aurora Ltda");
        request.setCnpj("11422377000155");
        request.setLogradouro("Rua das Industrias");
        request.setMunicipio("Sao Paulo");
        request.setNumero("1200");
        request.setComplemento("Bloco A");
        request.setQuantidade(120);
        request.setCep("01310-100");
        request.setTelefone("1130001100");
        request.setEmail("contato@metalurgicaaurora.com.br");
        request.setSite("https://www.metalurgicaaurora.com.br");
        request.setUsuario("admin.aurora");
        request.setSenha("Senha@1");
        return request;
    }

    /**
     * Diz se o campo informado tem algum erro de validacao.
     */
    private boolean temErro(EmpresaRequest request, String campo) {
        Set<ConstraintViolation<EmpresaRequest>> erros = VALIDADOR.validate(request);
        return erros.stream().anyMatch(violacao -> violacao.getPropertyPath().toString().equals(campo));
    }

    /**
     * Os telefones que a API aceita.
     *
     * A mesma lista do frontend (utils/mascaras.js) e a mesma que a
     * massa de dados usa. Se uma ponta aceitar e a outra nao, o
     * usuario ve um erro que nao existe.
     */
    @Test
    @DisplayName("O telefone e' aceito em todas as formas que o usuario digita")
    void telefoneAceitoEmTodasAsFormas() {

        String[] formas = {
            "1130001100",        // 10 digitos, sem mascara
            "11300011000",       // 11 digitos, sem mascara
            "(11) 3000-1100",    // com mascara
            "(11) 30001-1100",   // com mascara, 9 digitos
            "113000-1100",       // com traco, sem parenteses
            "11 3000-1100"       // com espaco
        };

        for (String forma : formas) {
            EmpresaRequest request = empresaValida();
            request.setTelefone(forma);

            assertFalse(temErro(request, "telefone"),
                    "O telefone \"" + forma + "\" deveria ser aceito.");
        }
    }

    /**
     * O telefone da propria massa de dados tem que passar.
     *
     * Este e' o teste que documenta o bug: com o regex antigo, este
     * telefone era recusado, e a empresa de id 1 nao podia ser
     * editada pela API.
     */
    @Test
    @DisplayName("O telefone da massa de dados e' aceito")
    void telefoneDaMassaDeDadosEaceito() {

        EmpresaRequest request = empresaValida();
        request.setTelefone("1130001100");

        assertFalse(temErro(request, "telefone"),
                "O telefone da massa de dados nao pode ser recusado pela validacao.");
    }

    /**
     * Telefone invalido e' recusado.
     */
    @Test
    @DisplayName("Telefone invalido e' recusado")
    void telefoneInvalidoERecusado() {

        String[] invalidos = {
            "123",               // curto demais
            "abcdefghij",       // letras
            "113000110000",     // longo demais
            "() 3000-1100"      // DDD sem digitos
        };

        for (String telefone : invalidos) {
            EmpresaRequest request = empresaValida();
            request.setTelefone(telefone);

            assertTrue(temErro(request, "telefone"),
                    "O telefone invalido \"" + telefone + "\" deveria ser recusado.");
        }
    }

    /**
     * O CNPJ e' aceito nas duas formas - com e sem pontuacao.
     */
    @Test
    @DisplayName("O CNPJ e' aceito com e sem pontuacao")
    void cnpjAceitoComESemPontuacao() {

        String[] formas = {
            "11422377000155",
            "11.422.377/0001-55"
        };

        for (String forma : formas) {
            EmpresaRequest request = empresaValida();
            request.setCnpj(forma);

            assertFalse(temErro(request, "cnpj"),
                    "O CNPJ \"" + forma + "\" deveria ser aceito.");
        }
    }

    /**
     * O CEP e' aceito com e sem traco.
     */
    @Test
    @DisplayName("O CEP e' aceito com e sem traco")
    void cepAceitoComESemTraco() {

        String[] formas = {"01310100", "01310-100"};

        for (String forma : formas) {
            EmpresaRequest request = empresaValida();
            request.setCep(forma);

            assertFalse(temErro(request, "cep"),
                    "O CEP \"" + forma + "\" deveria ser aceito.");
        }
    }

    /**
     * A razao social e' obrigatoria.
     */
    @Test
    @DisplayName("A razao social e' obrigatoria")
    void razaoSocialEObrigatoria() {

        EmpresaRequest request = empresaValida();
        request.setRazaoSocial("   ");

        assertTrue(temErro(request, "razaoSocial"),
                "Uma razao social so com espacos deveria ser recusada.");
    }

    /**
     * A senha vazia vira null (o "setter" escrito a mao).
     *
     * E' o que permite editar uma empresa sem trocar a senha: o
     * formulario manda "" e o DTO vira null, que o "@Size" aceita.
     */
    @Test
    @DisplayName("A senha vazia e' tratada como null (nao troca a senha)")
    void senhaVaziaViraNull() {

        EmpresaRequest request = empresaValida();
        request.setSenha("");

        assertTrue(request.getSenha() == null,
                "A senha vazia deveria virar null.");

        request.setSenha("   ");
        assertTrue(request.getSenha() == null,
                "A senha so com espacos deveria virar null.");
    }

    /**
     * Uma senha curta de verdade continua sendo recusada.
     *
     * A conversao de "" para null nao pode abrir a porta para uma
     * senha de 3 caracteres.
     */
    @Test
    @DisplayName("Uma senha curta de verdade continua sendo recusada")
    void senhaCurtaContinuaRecusada() {

        EmpresaRequest request = empresaValida();
        request.setSenha("abc");

        assertTrue(temErro(request, "senha"),
                "Uma senha de 3 caracteres deveria ser recusada.");
    }
}
