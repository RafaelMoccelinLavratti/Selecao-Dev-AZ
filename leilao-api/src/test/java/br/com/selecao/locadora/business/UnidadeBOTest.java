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
 * ============================================================================
 *  TESTE: REGRAS DE NEGOCIO DA UNIDADE
 * ============================================================================
 *
 *  Mesma abordagem do EmpresaBOTest: duble do repositorio, sem banco de
 *  dados, para o teste rodar em qualquer maquina.
 *
 *  O que estes testes provam:
 *  1) O nome e gravado sem espacos sobrando (normalizacao).
 *  2) Nome repetido e recusado.
 *  3) Id inexistente devolve o erro de "nao encontrado" (HTTP 404).
 * ============================================================================
 */
class UnidadeBOTest {

    private UnidadeRepository unidadeRepository;
    private UnidadeBO unidadeBO;

    @BeforeEach
    void preparar() {
        unidadeRepository = mock(UnidadeRepository.class);
        unidadeBO = new UnidadeBO();
        ReflectionTestUtils.setField(unidadeBO, "unidadeRepository", unidadeRepository);

        when(unidadeRepository.existsByNome(anyString())).thenReturn(false);
        when(unidadeRepository.existsByNomeAndIdNot(anyString(), any())).thenReturn(false);
        when(unidadeRepository.saveAndFlush(any(Unidade.class))).thenAnswer(invocacao -> {
            Unidade unidade = invocacao.getArgument(0);
            if (unidade.getId() == null) {
                unidade.setId(1);
            }
            return unidade;
        });
    }

    @Test
    @DisplayName("O nome e gravado sem espacos sobrando")
    void normalizaONome() {
        UnidadeRequest request = new UnidadeRequest();
        request.setNome("   Galpao Industrial   ");

        assertEquals("Galpao Industrial", unidadeBO.salvar(request, null).getNome());
    }

    @Test
    @DisplayName("Nome repetido e recusado com mensagem clara")
    void recusaNomeDuplicado() {
        when(unidadeRepository.existsByNome("Galpao Industrial")).thenReturn(true);

        UnidadeRequest request = new UnidadeRequest();
        request.setNome("Galpao Industrial");

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> unidadeBO.salvar(request, null));

        assertTrue(erro.getMessage().contains("Galpao Industrial"));
    }

    @Test
    @DisplayName("Id inexistente devolve o erro de nao encontrado (HTTP 404)")
    void idInexistenteDevolveNaoEncontrado() {
        when(unidadeRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(RegistroNaoEncontradoException.class,
                () -> unidadeBO.buscarPorId(999));
    }

    @Test
    @DisplayName("Excluir uma unidade inexistente devolve o erro de nao encontrado")
    void excluirInexistenteDevolveNaoEncontrado() {
        when(unidadeRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(RegistroNaoEncontradoException.class,
                () -> unidadeBO.excluir(999));
    }

    @Test
    @DisplayName("Excluir remove o registro do banco")
    void excluirRemoveORegistro() {
        Unidade unidade = new Unidade();
        unidade.setId(7);
        unidade.setNome("Unidade Teste");
        when(unidadeRepository.findById(7)).thenReturn(Optional.of(unidade));

        unidadeBO.excluir(7);

        verify(unidadeRepository).delete(unidade);
    }
}
