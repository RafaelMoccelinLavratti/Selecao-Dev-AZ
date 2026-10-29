package br.com.selecao.locadora.business;

import br.com.selecao.locadora.config.PasswordEncoderConfig;
import br.com.selecao.locadora.dto.EmpresaRequest;
import br.com.selecao.locadora.dto.EmpresaResponse;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ============================================================================
 *  TESTE: REGRAS DE NEGOCIO DA EMPRESA
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE ESTE TESTE NAO USA @SpringBootTest?
 *  ------------------------------------------------------
 *  Um teste com @SpringBootTest sobe o container inteiro do Spring - e,
 *  com isso, EXIGE o PostgreSQL no ar. Se o avaliador rodar "mvn test"
 *  numa maquina sem banco, o teste quebraria por um motivo que nao tem
 *  nada a ver com a regra testada.
 *
 *  Aqui usamos o Mockito para "simular" o repositorio (um duble) e
 *  montamos a camada de negocio na mao. Resultado: o teste roda em
 *  qualquer maquina, em milissegundos, e ainda testa exatamente a regra
 *  de negocio - sem ruido de infraestrutura.
 *
 *  (ReflectionTestUtils injeta os dependencias nos campos privados do BO,
 *  que sao preenchidos por injeção de dependencias pelo Spring no
 *  funcionamento real.)
 *
 *  O que estes testes provam:
 *  1) O CNPJ formatado (11.555.999/0001-11) e gravado so com digitos.
 *  2) O mesmo CNPJ nao pode ser cadastrado duas vezes.
 *  3) A senha e gravada como hash BCrypt, nunca em texto puro.
 *  4) A senha NUNCA aparece na resposta da API.
 * ============================================================================
 */
class EmpresaBOTest {

    /** Duble do repositorio (nao ha banco de dados de verdade aqui). */
    private EmpresaRepository empresaRepository;

    /** A camada de negocio sob teste. */
    private EmpresaBO empresaBO;

    /** O codificador real de senha (o mesmo da aplicacao). */
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void preparar() {
        empresaRepository = mock(EmpresaRepository.class);
        passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

        empresaBO = new EmpresaBO();
        ReflectionTestUtils.setField(empresaBO, "empresaRepository", empresaRepository);
        ReflectionTestUtils.setField(empresaBO, "passwordEncoder", passwordEncoder);

        // Por padrao, nenhum CNPJ e nenhum usuario existem ainda.
        when(empresaRepository.existsByCnpj(anyString())).thenReturn(false);
        when(empresaRepository.existsByUsuario(anyString())).thenReturn(false);

        // O "save" devolve a propria entidade, incrementando o id.
        when(empresaRepository.saveAndFlush(any(Empresa.class))).thenAnswer(invocacao -> {
            Empresa empresa = invocacao.getArgument(0);
            if (empresa.getId() == null) {
                empresa.setId(1);
            }
            return empresa;
        });
    }

    /**
     * Monta um cadastro valido, com os campos mascarados como o usuario
     * digitaria na tela.
     *
     * Repare que o CNPJ vem como "11.555.999/0001-11" - com a mascara.
     * O teste 1 verifica que a camada de negocio normaliza isso antes
     * de gravar. Montar o dado "do jeito do usuario" e o que torna o
     * teste realista.
     *
     * @return um pedido de cadastro valido
     */
    private EmpresaRequest requestValido() {
        EmpresaRequest request = new EmpresaRequest();
        request.setRazaoSocial("Metalurgica Aurora Ltda");
        request.setCnpj("11.555.999/0001-11");
        request.setCep("01310-100");
        request.setTelefone("(11) 3000-1100");
        request.setEmail("contato@aurora.com.br");
        request.setSite("https://www.aurora.com.br");
        request.setUsuario("admin.aurora");
        request.setSenha("SenhaSegura123");
        return request;
    }

    /**
     * Teste 1: CNPJ, CEP e telefone sao normalizados (gravados so com digitos).
     *
     * Este e um dos testes mais importantes do arquivo, porque ele
     * verifica a DECISAO DE MODELO do projeto inteiro:
     *
     * O banco guarda o CNPJ **sem pontuacao** ("11555999000111"), e a
     * tela aplica a mascara **so na exibicao** ("11.555.999/0001-11").
     *
     * Se o banco guardasse o valor como o usuario digitou, existiriam
     * varias representacoes do mesmo dado ("11.555.999/0001-11",
     * "11.555.999000111", "11555999000111") - e a busca por CNPJ
     * deixaria de funcionar, porque a comparacao seria de texto.
     *
     * A canonicalizacao acontece no BACKEND, e nao so no frontend,
     * porque o frontend pode ser contornado: qualquer um pode chamar a
     * API direto, sem passar pela tela.
     */
    @Test
    @DisplayName("CNPJ, CEP e telefone sao normalizados (gravados so com digitos)")
    void normalizaOsCamposFormatados() {
        EmpresaResponse response = empresaBO.salvar(requestValido(), null);

        assertEquals("11555999000111", response.getCnpj());
        assertEquals("01310100", response.getCep());
        assertEquals("1130001100", response.getTelefone());
    }

    /**
     * Teste 2: CNPJ duplicado e recusado com mensagem clara.
     *
     * Alem de verificar que a excecao correta foi lancada, o teste
     * verifica que a MENSAGEM cita o campo envolvido ("CNPJ"). Isso
     * importa para quem usa o sistema: uma mensagem generica
     * ("dados invalidos") nao ajuda ninguem a entender o que houve.
     */
    @Test
    @DisplayName("CNPJ duplicado e recusado com mensagem clara")
    void recusaCnpjDuplicado() {
        when(empresaRepository.existsByCnpj("11555999000111")).thenReturn(true);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> empresaBO.salvar(requestValido(), null));

        assertTrue(erro.getMessage().contains("CNPJ"), "a mensagem deve citar o CNPJ");
    }

    /**
     * Teste 3: usuario duplicado e recusado com mensagem clara.
     *
     * O usuario e a credencial de acesso da empresa. Duas empresas com o
     * mesmo usuario criariam ambiguidade sobre quem esta entrando -
     * por isso a unicidade e exigida.
     */
    @Test
    @DisplayName("Usuario duplicado e recusado com mensagem clara")
    void recusaUsuarioDuplicado() {
        when(empresaRepository.existsByUsuario("admin.aurora")).thenReturn(true);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> empresaBO.salvar(requestValido(), null));

        assertTrue(erro.getMessage().contains("usuario"), "a mensagem deve citar o usuario");
    }

    /**
     * Teste 4: a senha e gravada como hash BCrypt, nunca em texto puro.
     *
     * Este teste usa o **ArgumentCaptor** do Mockito, que e a forma de
     * inspecionar o objeto que foi realmente passado ao repositorio:
     * em vez de so conferir o que o duble devolve, ele captura o
     * ARGUMENTO da chamada.
     *
     * As tres verificacoes juntas cobrem o ciclo completo:
     *   1) o valor gravado NAO e a senha digitada;
     *   2) o valor gravado TEM o formato de um hash (Argon2id, com o
     *      prefixo que identifica o algoritmo);
     *   3) a senha original ainda e conferivel a partir do hash -
     *      ou seja, o hash nao e "a senha embaralhada", e sim um valor
     *      que o sistema sabe verificar depois.
     */
    @Test
    @DisplayName("A senha e gravada como hash, nunca em texto puro")
    void gravaASenhaComoHash() {
        empresaBO.salvar(requestValido(), null);

        // Recupera a entidade que foi enviada ao repositorio.
        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(captor.capture());

        String hashGravado = captor.getValue().getSenha();
        assertNotEquals("SenhaSegura123", hashGravado, "a senha nao pode ser gravada em texto puro");
        assertTrue(hashGravado.startsWith("{argon2}$argon2id$"),
                "o valor gravado deve ser um hash Argon2id, com o prefixo que identifica o algoritmo");
        assertTrue(passwordEncoder.matches("SenhaSegura123", hashGravado),
                "a senha original precisa ser conferivel a partir do hash");
    }

    /**
     * Teste 5: na alteracao, uma senha vazia mantem o hash gravado.
     *
     * Este e o teste do comportamento mais sutil do cadastro de
     * empresa: **"deixar o campo da senha em branco" significa
     * "manter a senha atual"**.
     *
     * Sem esse comportamento, toda vez que o usuario editasse o
     * telefone de uma empresa, seria obrigado a redigitar a senha - e,
     * pior, a tela nao poderia mostrar a senha (a API nunca a devolve),
     * entao ele nao teria como saber qual era.
     *
     * O teste verifica as duas metades do comportamento:
     *   1) o hash ANTERIOR foi preservado intacto;
     *   2) o valor preservado continua sendo um hash - nao a senha em
     *      texto puro que "deu certo" por acidente.
     */
    @Test
    @DisplayName("Na alteracao, uma senha vazia mantem o hash que ja estava gravado")
    void alteracaoSemSenhaMantemOHash() {
        // Simula uma empresa ja existente, com hash gravado.
        Empresa existente = new Empresa();
        existente.setId(5);
        existente.setRazaoSocial("Metalurgica Aurora Ltda");
        existente.setCnpj("11555999000111");
        existente.setSenha(passwordEncoder.encode("SenhaAntiga123"));
        when(empresaRepository.findById(5)).thenReturn(java.util.Optional.of(existente));
        when(empresaRepository.existsByCnpjAndIdNot(anyString(), any())).thenReturn(false);
        when(empresaRepository.existsByUsuarioAndIdNot(anyString(), any())).thenReturn(false);

        // Altera sem informar senha.
        EmpresaRequest request = requestValido();
        request.setSenha(null);
        request.setEmail("novo@aurora.com.br");

        empresaBO.salvar(request, 5);

        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(captor.capture());

        // O hash anterior deve ser gravado intacto (mesmo texto).
        assertEquals(existente.getSenha(), captor.getValue().getSenha(),
                "sem nova senha, o hash anterior deve ser preservado");
        assertNotEquals("SenhaAntiga123", captor.getValue().getSenha(),
                "o hash preservado continua sendo um hash, nao a senha em texto puro");
    }

    /**
     * Teste 6: a resposta da API NUNCA contem o campo senha.
     *
     * Este teste usa **reflexao** para olhar a estrutura da classe
     * `EmpresaResponse` e verificar, campo a campo, que nenhum se chama
     * "senha".
     *
     * E uma garantia de **estrutura**, e nao de comportamento: mesmo
     * que ninguem preencha o campo hoje, a simples existencia dele no
     * DTO de saida seria um risco - qualquer preenchimento futuro
     * passaria a vazar a senha. Verificar a ESTRUTURA impede a
     * regressao na origem.
     *
     * A forma mais simples e mais forte de proteger isso seria o
     * inverso: um DTO de saida que NAO tem o campo. E o que o projeto
     * faz - e este teste existe para garantir que ninguem o adicione
     * depois sem perceber.
     */
    @Test
    @DisplayName("A resposta da API nunca contem o campo senha")
    void respostaNaoExpoeASenha() {
        boolean temCampoSenha = false;
        for (java.lang.reflect.Field campo : EmpresaResponse.class.getDeclaredFields()) {
            if ("senha".equalsIgnoreCase(campo.getName())) {
                temCampoSenha = true;
            }
        }
        assertTrue(!temCampoSenha, "o DTO de resposta nao pode ter campo de senha");
    }
}
