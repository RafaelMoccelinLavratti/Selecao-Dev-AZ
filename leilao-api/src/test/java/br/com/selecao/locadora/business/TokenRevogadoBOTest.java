package br.com.selecao.locadora.business;

import br.com.selecao.locadora.entity.TokenRevogado;
import br.com.selecao.locadora.repository.TokenRevogadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes da lista de bloqueio das sessoes revogadas.
 *
 * O problema que esta lista resolve: o token de acesso e um JWT
 * stateless, entao o logout nao encerra a sessao de imediato - o token
 * continua valido ate expirar (15 minutos).
 *
 * Aqui se prova que a lista funciona: uma familia revogada bloqueia, e
 * o bloqueio expira sozinho quando nao ha mais token valido.
 */
class TokenRevogadoBOTest {

    private static final String FAMILIA = "familia-de-teste-123";

    private TokenRevogadoBO tokenRevogadoBO;

    private TokenRevogadoRepository repository;

    @BeforeEach
    void preparar() {

        repository = mock(TokenRevogadoRepository.class);

        tokenRevogadoBO = new TokenRevogadoBO();
        ReflectionTestUtils.setField(tokenRevogadoBO, "tokenRevogadoRepository", repository);
    }

    /**
     * Teste 1: revogar a familia registra a familia na lista.
     */
    @Test
    @DisplayName("Revogar a familia registra a familia na lista de bloqueio")
    void revogarRegistraAFamilia() {

        when(repository.findById(FAMILIA)).thenReturn(Optional.empty());

        tokenRevogadoBO.revogarFamilia(FAMILIA, "admin", LocalDateTime.now().plusMinutes(15));

        verify(repository).save(any(TokenRevogado.class));
    }

    /**
     * Teste 2: a familia revogada bloqueia.
     *
     * E o teste que fecha o logout: com a familia na lista, o filtro de
     * JWT recusa o token de acesso, mesmo que ele ainda esteja dentro
     * do prazo.
     */
    @Test
    @DisplayName("A familia revogada bloqueia o token de acesso")
    void familiaRevogadaBloqueia() {

        TokenRevogado registro = new TokenRevogado();
        registro.setChave(FAMILIA);
        registro.setExpiraEm(LocalDateTime.now().plusMinutes(15));

        when(repository.findById(FAMILIA)).thenReturn(Optional.of(registro));

        assertTrue(tokenRevogadoBO.familiaRevogada(FAMILIA),
                "A familia revogada precisa bloquear o token de acesso.");
    }

    /**
     * Teste 3: a familia que nao foi revogada nao bloqueia.
     */
    @Test
    @DisplayName("A familia que nao foi revogada nao bloqueia")
    void familiaNaoRevogadaNaoBloqueia() {

        when(repository.findById(FAMILIA)).thenReturn(Optional.empty());

        assertFalse(tokenRevogadoBO.familiaRevogada(FAMILIA),
                "Uma sessao normal nao pode ser bloqueada.");
    }

    /**
     * Teste 4: o bloqueio expira sozinho.
     *
     * Passado o prazo do token mais antigo da familia, nenhum token
     * dela vale mais - e o registro so ocuparia espaco.
     */
    @Test
    @DisplayName("O bloqueio expira quando nenhum token da familia vale mais")
    void bloqueioExpiraSozinho() {

        TokenRevogado registro = new TokenRevogado();
        registro.setChave(FAMILIA);
        registro.setExpiraEm(LocalDateTime.now().minusMinutes(1));

        when(repository.findById(FAMILIA)).thenReturn(Optional.of(registro));

        assertFalse(tokenRevogadoBO.familiaRevogada(FAMILIA),
                "Um bloqueio vencido nao pode continuar bloqueando.");
    }

    /**
     * Teste 5: token sem familia nao e bloqueado.
     *
     * Token emitido antes da revogacao existir, ou montado a mao, nao
     * tem familia. Nao ha como saber se foi revogado, entao o filtro
     * deixa passar - como antes.
     */
    @Test
    @DisplayName("Token sem familia nao e bloqueado")
    void tokenSemFamiliaNaoEBloqueado() {

        assertFalse(tokenRevogadoBO.familiaRevogada(null));
        assertFalse(tokenRevogadoBO.familiaRevogada(""));
        assertFalse(tokenRevogadoBO.familiaRevogada("   "));

        verify(repository, never()).findById(anyString());
    }

    /**
     * Teste 6: revogar duas vezes nao duplica o registro.
     *
     * A chave e primaria: um INSERT falharia. O BO atualiza o prazo
     * do registro que ja existe.
     */
    @Test
    @DisplayName("Revogar duas vezes atualiza o registro, em vez de duplicar")
    void revogarDuasVezesAtualiza() {

        TokenRevogado existente = new TokenRevogado();
        existente.setChave(FAMILIA);
        existente.setExpiraEm(LocalDateTime.now().plusMinutes(5));

        when(repository.findById(FAMILIA)).thenReturn(Optional.of(existente));

        tokenRevogadoBO.revogarFamilia(FAMILIA, "admin", LocalDateTime.now().plusMinutes(15));

        // Um unico save (o do registro existente), e nenhum INSERT novo.
        verify(repository, times(1)).save(any(TokenRevogado.class));
        assertTrue(existente.getExpiraEm().isAfter(LocalDateTime.now().plusMinutes(10)),
                "O prazo do bloqueio deve ter sido estendido.");
    }

    /**
     * Teste 7: a limpeza remove os registros vencidos.
     */
    @Test
    @DisplayName("A limpeza remove os registros vencidos")
    void limpezaRemoveOsVencidos() {

        when(repository.removerExpirados(any(LocalDateTime.class))).thenReturn(3);

        assertTrue(tokenRevogadoBO.limparExpirados() == 3);
    }
}
