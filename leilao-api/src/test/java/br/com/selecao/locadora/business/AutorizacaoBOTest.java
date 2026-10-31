package br.com.selecao.locadora.business;

import br.com.selecao.locadora.entity.Comprador;
import br.com.selecao.locadora.entity.CompradorId;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.entity.Lote;
import br.com.selecao.locadora.exception.AcessoNegadoException;
import br.com.selecao.locadora.repository.CompradorRepository;
import br.com.selecao.locadora.repository.LeilaoRepository;
import br.com.selecao.locadora.repository.LoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes das regras de acesso entre empresas.
 *
 * Este arquivo prova a coisa que faltava no projeto: que uma empresa
 * autenticada nao alcança os dados de outra.
 *
 * O cenario e' o mesmo de sempre - a empresa 10 vende o leilao 100, a
 * empresa 20 esta inscrita nele como compradora, e a empresa 30 nao tem
 * relacao com nenhum dos dois.
 */
class AutorizacaoBOTest {

    private static final Integer EMPRESA_VENDEDORA = 10;
    private static final Integer EMPRESA_COMPRADORA = 20;
    private static final Integer EMPRESA_ESTRANGEIRA = 30;
    private static final Integer LEILAO = 100;

    private AutorizacaoBO autorizacaoBO;

    private LeilaoRepository leilaoRepository;
    private CompradorRepository compradorRepository;
    private LoteRepository loteRepository;

    @BeforeEach
    void preparar() {

        leilaoRepository = mock(LeilaoRepository.class);
        compradorRepository = mock(CompradorRepository.class);
        loteRepository = mock(LoteRepository.class);

        autorizacaoBO = new AutorizacaoBO();
        ReflectionTestUtils.setField(autorizacaoBO, "leilaoRepository", leilaoRepository);
        ReflectionTestUtils.setField(autorizacaoBO, "compradorRepository", compradorRepository);
        ReflectionTestUtils.setField(autorizacaoBO, "loteRepository", loteRepository);

        // O leilao 100 e' vendido pela empresa 10.
        when(leilaoRepository.findById(LEILAO)).thenReturn(Optional.of(leilaoDe(LEILAO, EMPRESA_VENDEDORA)));

        // A empresa 20 esta inscrita nele como compradora.
        when(compradorRepository.findByIdLeilao(LEILAO))
                .thenReturn(Collections.singletonList(vinculo(EMPRESA_COMPRADORA, LEILAO)));
    }

    /**
     * Limpa o contexto de seguranca depois de cada teste: um usuario
     * autenticado que vaza para o teste seguinte faz o teste seguinte
     * passar (ou falhar) pelo motivo errado.
     */
    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Administrador
    // ------------------------------------------------------------------

    /**
     * O administrador ve tudo: qualquer leilao, de qualquer empresa.
     */
    @Test
    @DisplayName("O administrador pode ver qualquer leilao")
    void administradorPodeVerQualquerLeilao() {

        autenticarComo(Papeis.ADMIN, null);

        assertDoesNotThrow(() -> autorizacaoBO.exigirLeituraLeilao(LEILAO));
        assertDoesNotThrow(() -> autorizacaoBO.exigirEscritaLeilao(LEILAO));
        assertDoesNotThrow(() -> autorizacaoBO.exigirAdministrador("ler a trilha"));
    }

    // ------------------------------------------------------------------
    // Vendedora
    // ------------------------------------------------------------------

    /**
     * A vendedora ve e altera o proprio leilao.
     */
    @Test
    @DisplayName("A vendedora pode ver e alterar o proprio leilao")
    void vendedoraPodeVerEAlterarOProprioLeilao() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertDoesNotThrow(() -> autorizacaoBO.exigirLeituraLeilao(LEILAO));
        assertDoesNotThrow(() -> autorizacaoBO.exigirEscritaLeilao(LEILAO));
    }

    /**
     * A vendedora nao mexe no leilao de outra empresa.
     */
    @Test
    @DisplayName("A vendedora nao pode alterar o leilao de outra empresa")
    void vendedoraNaoPodeAlterarLeilaoDeOutra() {

        when(leilaoRepository.findById(101)).thenReturn(Optional.of(leilaoDe(101, EMPRESA_ESTRANGEIRA)));

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirEscritaLeilao(101));
    }

    // ------------------------------------------------------------------
    // Compradora
    // ------------------------------------------------------------------

    /**
     * A compradora ve o leilao em que esta inscrita, mas nao altera.
     *
     * A distincao importa: quem compra pode acompanhar o leilao, mas
     * nao pode mexer nos lotes de outra empresa.
     */
    @Test
    @DisplayName("A compradora pode ver o leilao, mas nao pode alterar")
    void compradoraPodeVerMasNaoAlterar() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_COMPRADORA);

        assertDoesNotThrow(() -> autorizacaoBO.exigirLeituraLeilao(LEILAO));
        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirEscritaLeilao(LEILAO));
    }

    // ------------------------------------------------------------------
    // Empresa sem relacao
    // ------------------------------------------------------------------

    /**
     * O ataque principal: uma empresa autenticada que tenta ler os
     * dados de outra.
     *
     * Este e' o teste que prova que o IDOR esta fechado.
     */
    @Test
    @DisplayName("Uma empresa sem relacao nao acessa o leilao de outra")
    void empresaSemRelacaoNaoAcessaLeilaoDeOutra() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_ESTRANGEIRA);

        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirLeituraLeilao(LEILAO));
        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirEscritaLeilao(LEILAO));
    }

    /**
     * A empresa estrangeira tambem nao ve os lotes.
     */
    @Test
    @DisplayName("Uma empresa sem relacao nao acessa os lotes de outra")
    void empresaSemRelacaoNaoAcessaOsLotes() {

        when(loteRepository.findById(1)).thenReturn(Optional.of(loteDe(1, LEILAO)));

        autenticarComo(Papeis.EMPRESA, EMPRESA_ESTRANGEIRA);

        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirLeituraLote(1));
        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirEscritaLote(1));
    }

    /**
     * A empresa estrangeira nao altera os lotes de outra.
     */
    @Test
    @DisplayName("Uma empresa sem relacao nao altera os lotes de outra")
    void empresaSemRelacaoNaoAlteraOsLotes() {

        when(loteRepository.findById(1)).thenReturn(Optional.of(loteDe(1, LEILAO)));

        // A vendedora pode mexer nos lotes do proprio leilao.
        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);
        assertDoesNotThrow(() -> autorizacaoBO.exigirEscritaLote(1));

        // A compradora ve os lotes, mas nao altera.
        autenticarComo(Papeis.EMPRESA, EMPRESA_COMPRADORA);
        assertDoesNotThrow(() -> autorizacaoBO.exigirLeituraLote(1));
        assertThrows(AcessoNegadoException.class, () -> autorizacaoBO.exigirEscritaLote(1));
    }

    // ------------------------------------------------------------------
    // Troca de referencia (escalacao de privilegio)
    // ------------------------------------------------------------------

    /**
     * A empresa vendedora nao pode transferir o proprio leilao para
     * outra empresa.
     *
     * Este e' o teste que fecha a brecha que a verificacao de dono
     * sozinha deixava aberta. Sem ele, o "dono atual" do leilao e a
     * empresa que aparece depois da troca: o controle de acesso viraria
     * uma roleta.
     */
    @Test
    @DisplayName("A vendedora nao pode transferir o leilao para outra empresa")
    void vendedoraNaoPodeTransferirLeilaoParaOutra() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoBO.exigirEscritaLeilaoComVendedor(LEILAO, EMPRESA_ESTRANGEIRA));
    }

    /**
     * A empresa vendedora pode alterar o proprio leilao mantendo-se
     * como vendedora. E o caminho legitimo.
     */
    @Test
    @DisplayName("A vendedora pode alterar o proprio leilao sem trocar o vendedor")
    void vendedoraPodeAlterarOProprioLeilaoMantendoOVendedor() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertDoesNotThrow(
                () -> autorizacaoBO.exigirEscritaLeilaoComVendedor(LEILAO, EMPRESA_VENDEDORA));
    }

    /**
     * O administrador pode transferir o leilao: e a operacao da
     * administracao da API.
     */
    @Test
    @DisplayName("O administrador pode transferir o leilao para outra empresa")
    void administradorPodeTransferirLeilao() {

        autenticarComo(Papeis.ADMIN, null);

        assertDoesNotThrow(
                () -> autorizacaoBO.exigirEscritaLeilaoComVendedor(LEILAO, EMPRESA_ESTRANGEIRA));
    }

    /**
     * Uma empresa sem relacao nao move o lote para o proprio leilao.
     *
     * O lote pertence ao leilao da empresa 10. Se a empresa 30 pudesse
     * mover o lote para um leilao dela, ficaria com o controle do valor
     * inicial de um item que e de outra.
     */
    @Test
    @DisplayName("Uma empresa sem relacao nao move o lote para o proprio leilao")
    void empresaEstrangeiraNaoMoveOLoteParaOCeuProprioLeilao() {

        when(loteRepository.findById(1)).thenReturn(Optional.of(loteDe(1, LEILAO)));

        // O leilao 200 e' vendido pela empresa 30.
        when(leilaoRepository.findById(200)).thenReturn(Optional.of(leilaoDe(200, EMPRESA_ESTRANGEIRA)));

        autenticarComo(Papeis.EMPRESA, EMPRESA_ESTRANGEIRA);

        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoBO.exigirEscritaLoteComLeilao(1, 200));
    }

    /**
     * A vendedora pode mexer no lote do proprio leilao.
     */
    @Test
    @DisplayName("A vendedora pode alterar o lote do proprio leilao")
    void vendedoraPodeAlterarOLoteDoProprioLeilao() {

        when(loteRepository.findById(1)).thenReturn(Optional.of(loteDe(1, LEILAO)));

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertDoesNotThrow(() -> autorizacaoBO.exigirEscritaLoteComLeilao(1, LEILAO));
    }

    // ------------------------------------------------------------------
    // Vinculo de comprador
    // ------------------------------------------------------------------

    /**
     * O vinculo de comprador tem a chave composta (empresa + leilao).
     * Uma empresa so ve o proprio vinculo.
     */
    @Test
    @DisplayName("Uma empresa so ve o proprio vinculo de comprador")
    void empresaSoVeOProprioVinculo() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_COMPRADORA);

        assertDoesNotThrow(() -> autorizacaoBO.exigirLeituraComprador(EMPRESA_COMPRADORA));
        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoBO.exigirLeituraComprador(EMPRESA_VENDEDORA));
    }

    // ------------------------------------------------------------------
    // Operacoes restritas ao administrador
    // ------------------------------------------------------------------

    /**
     * As operacoes da administracao nao sao da empresa.
     */
    @Test
    @DisplayName("O usuario de uma empresa nao executa operacao de administracao")
    void empresaNaoExecutaOperacaoDeAdministracao() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoBO.exigirAdministrador("cadastrar empresas"));
        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoBO.exigirAdministrador("ler a trilha de auditoria"));
    }

    /**
     * E o cadastro da propria empresa: o usuario pode alterar o proprio
     * cadastro, mas nao o de outra.
     */
    @Test
    @DisplayName("O usuario pode alterar o proprio cadastro, mas nao o de outra empresa")
    void usuarioAlteraProprioCadastroE_naoDeOutra() {

        autenticarComo(Papeis.EMPRESA, EMPRESA_VENDEDORA);

        assertDoesNotThrow(() -> autorizacaoBO.exigirProprioOuAdministrador(EMPRESA_VENDEDORA));
        assertThrows(AcessoNegadoException.class,
                () -> autorizacaoBO.exigirProprioOuAdministrador(EMPRESA_ESTRANGEIRA));
    }

    // ------------------------------------------------------------------
    // Modo de desenvolvimento
    // ------------------------------------------------------------------

    /**
     * Sem autenticacao (o perfil de desenvolvimento, aberto de
     * proposito), nenhum controle e aplicado.
     */
    @Test
    @DisplayName("Sem autenticacao, nenhum controle e aplicado (perfil de desenvolvimento)")
    void semAutenticacaoNenhumControleEhAplicado() {

        SecurityContextHolder.clearContext();

        assertDoesNotThrow(() -> autorizacaoBO.exigirLeituraLeilao(LEILAO));
        assertDoesNotThrow(() -> autorizacaoBO.exigirEscritaLeilao(LEILAO));
        assertDoesNotThrow(() -> autorizacaoBO.exigirAdministrador("ler a trilha"));
        assertDoesNotThrow(() -> autorizacaoBO.exigirProprioOuAdministrador(EMPRESA_ESTRANGEIRA));
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    /**
     * Coloca um usuario no contexto de seguranca.
     */
    private void autenticarComo(String papel, Integer empresaId) {

        UsuarioAutenticado usuario = new UsuarioAutenticado("usuario-teste", "hash", empresaId, papel);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
    }

    /**
     * Monta um leilao vendido pela empresa informada.
     */
    private Leilao leilaoDe(Integer id, Integer empresaVendedora) {

        Empresa vendedora = new Empresa();
        vendedora.setId(empresaVendedora);

        Leilao leilao = new Leilao();
        leilao.setId(id);
        leilao.setVendedor(vendedora);

        return leilao;
    }

    /**
     * Monta um lote de um leilao.
     */
    private Lote loteDe(Integer id, Integer leilaoId) {

        Lote lote = new Lote();
        lote.setId(id);
        lote.setLeilao(leilaoDe(leilaoId, EMPRESA_VENDEDORA));

        return lote;
    }

    /**
     * Monta um vinculo de comprador.
     */
    private Comprador vinculo(Integer empresaId, Integer leilaoId) {

        CompradorId id = new CompradorId();
        id.setEmpresa(empresaId);
        id.setLeilao(leilaoId);

        return Comprador.builder().id(id).build();
    }
}
