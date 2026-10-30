package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.UnidadeRequest;
import br.com.selecao.locadora.entity.Unidade;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.exception.RegistroNaoEncontradoException;
import br.com.selecao.locadora.repository.UnidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes das regras de negocio da unidade.
 *
 * O repositorio e dublado com o Mockito (um "duble" e um objeto falso que
 * se passa pelo original), entao o teste roda sem banco.
 */
class UnidadeBOTest {

    /**
     * Duble do repositorio (nao ha banco de dados de verdade aqui).
     */
    private UnidadeRepository unidadeRepository;

    /**
     * A camada de negocio sob teste.
     */
    private UnidadeBO unidadeBO;

    /**
     * Prepara o cenario de cada teste.
     *
     * O "@BeforeEach" roda antes de cada metodo de teste. E o
     * lugar de criar os dubles e definir o comportamento padrao.
     *
     * Repare que o comportamento padrao e "nada existe ainda"
     * (os dois "existsBy..." devolvem false). Assim, cada teste so
     * precisa configurar o que e relevante pra ele - e nao precisa
     * redefinir tudo.
     */
    @BeforeEach
    void preparar() {

        // Cria o duble do repositorio.
        unidadeRepository = mock(UnidadeRepository.class);

        // Cria a camada de negocio "na mao".
        unidadeBO = new UnidadeBO();

        // Injeta o duble no campo privado do BO (em producao, quem faz
        // isso e o Spring, com a anotacao @Autowired).
        ReflectionTestUtils.setField(unidadeBO, "unidadeRepository", unidadeRepository);

        // Comeca sem nenhum nome cadastrado.
        when(unidadeRepository.existsByNome(anyString())).thenReturn(false);
        when(unidadeRepository.existsByNomeAndIdNot(anyString(), any())).thenReturn(false);

        // Comportamento padrao do "saveAndFlush": devolve a entidade
        // recebida, atribuindo um id quando for uma inclusao (id nulo).
        // O "thenAnswer" permite que o duble inspecione o argumento
        // recebido - e assim o id so e atribuido na inclusao.
        when(unidadeRepository.saveAndFlush(any(Unidade.class))).thenAnswer(invocacao -> {
            Unidade unidade = invocacao.getArgument(0);
            if (unidade.getId() == null) {
                unidade.setId(1);
            }
            return unidade;
        });
    }

    /**
     * Teste 1: o nome e gravado SEM espacos sobrando.
     *
     * A regra e "normalizacao": antes de gravar, o espaco das pontas
     * e removido. Isso impede que "Galpao Industrial" e "Galpao
     * Industrial " (com espaco no fim) sejam gravados como dois
     * registros diferentes - e que a busca por um deles falhe.
     *
     * E uma normalizacao no backend (e nao so no frontend), porque o
     * frontend pode ser contornado: qualquer um pode chamar a API
     * direto, sem passar pela tela.
     */
    @Test
    @DisplayName("O nome e gravado sem espacos sobrando")
    void normalizaONome() {

        // Prepara o pedido com espacos sobrando.
        UnidadeRequest request = new UnidadeRequest();
        request.setNome("   Galpao Industrial   ");

        // Chama o negocio e verifica o que foi salvo.
        assertEquals("Galpao Industrial", unidadeBO.salvar(request, null).getNome(),
                "O nome deveria ter sido gravado sem os espacos das pontas.");
    }

    /**
     * Teste 2: nome repetido e recusado com mensagem clara.
     *
     * Alem de verificar que a excecao correta foi lancada, o teste
     * verifica que a mensagem contem o nome duplicado. Isso importa
     * para o usuario: uma mensagem generica ("nome invalido") nao
     * ajuda ninguem a entender o que aconteceu.
     */
    @Test
    @DisplayName("Nome repetido e recusado com mensagem clara")
    void recusaNomeDuplicado() {

        // Este nome ja esta cadastrado.
        when(unidadeRepository.existsByNome("Galpao Industrial")).thenReturn(true);

        UnidadeRequest request = new UnidadeRequest();
        request.setNome("Galpao Industrial");

        // "assertThrows" espera que a excecao seja lancada e devolve
        // o objeto da excecao, para inspecao.
        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> unidadeBO.salvar(request, null));

        assertTrue(erro.getMessage().contains("Galpao Industrial"),
                "A mensagem do erro deveria citar o nome duplicado, veio: " + erro.getMessage());
    }

    /**
     * Teste 3: id inexistente devolve o erro de "nao encontrado".
     *
     * Esta excecao e o que o GlobalExceptionHandler transforma em
     * HTTP 404. O teste garante que o caminho de erro existe - e nao
     * um NullPointerException em algum lugar mais adiante.
     */
    @Test
    @DisplayName("Id inexistente devolve o erro de nao encontrado (HTTP 404)")
    void idInexistenteDevolveNaoEncontrado() {

        // O repositorio devolve "vazio" (Optional.empty) para o id 999.
        when(unidadeRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(RegistroNaoEncontradoException.class,
                () -> unidadeBO.buscarPorId(999));
    }

    /**
     * Teste 4: excluir uma unidade inexistente devolve o erro.
     *
     * Sem esta verificacao, o "delete" rodaria com um id que nao
     * existe - e a API responderia 200 para uma exclusao que nao
     * aconteceu.
     */
    @Test
    @DisplayName("Excluir uma unidade inexistente devolve o erro de nao encontrado")
    void excluirInexistenteDevolveNaoEncontrado() {

        when(unidadeRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(RegistroNaoEncontradoException.class,
                () -> unidadeBO.excluir(999));
    }

    /**
     * Teste 5: excluir remove o registro do banco.
     *
     * Aqui o teste verifica a interacao: o "verify" confirma que o
     * repositorio recebeu a ordem de exclusao da unidade certa.
     */
    @Test
    @DisplayName("Excluir remove o registro do banco")
    void excluirRemoveORegistro() {

        // Prepara uma unidade existente.
        Unidade unidade = new Unidade();
        unidade.setId(7);
        unidade.setNome("Unidade Teste");
        when(unidadeRepository.findById(7)).thenReturn(Optional.of(unidade));

        // Executa a exclusao.
        unidadeBO.excluir(7);

        // Confirma que o repositorio foi chamado com a mesma unidade.
        verify(unidadeRepository).delete(unidade);
    }
}
