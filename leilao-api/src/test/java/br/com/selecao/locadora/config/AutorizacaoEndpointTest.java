package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.AuditoriaBO;
import br.com.selecao.locadora.business.AutorizacaoBO;
import br.com.selecao.locadora.business.LeilaoBO;
import br.com.selecao.locadora.business.LoteBO;
import br.com.selecao.locadora.business.Papeis;
import br.com.selecao.locadora.business.TokenJwtBO;
import br.com.selecao.locadora.business.TokenRevogadoBO;
import br.com.selecao.locadora.dto.LeilaoRequest;
import br.com.selecao.locadora.dto.LoteRequest;
import br.com.selecao.locadora.entity.Comprador;
import br.com.selecao.locadora.entity.CompradorId;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.entity.Lote;
import br.com.selecao.locadora.repository.CompradorRepository;
import br.com.selecao.locadora.repository.LeilaoRepository;
import br.com.selecao.locadora.repository.LoteRepository;
import br.com.selecao.locadora.service.LeilaoService;
import br.com.selecao.locadora.service.LoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integracao da autorizacao por empresa, no nivel do endpoint.
 *
 * O AutorizacaoBOTest prova as regras de acesso da camada de negocio.
 * Este arquivo prova que as regras Chegam ao endpoint - ou seja, que o
 * controller realmente chama a verificacao antes de gravar.
 *
 * Por que os dois niveis?
 *
 * Uma regra de negocio testada na unidade pode nao estar ligada ao
 * controller. O metodo "exigirEscritaLeilao" pode passar todos os
 * testes unitarios e mesmo assim nao ser chamado no PUT - e a brecha
 * continua aberta. Este teste fecha essa porta: ele faz a requisicao
 * HTTP de verdade e verifica o status e o efeito colateral.
 *
 * O cenario e o mesmo do ArquizacaoBOTest: a empresa 10 vende o leilao
 * 100, e a empresa 30 nao tem relacao com ele.
 */
@WebMvcTest(controllers = {LeilaoService.class, LoteService.class})
@Import({
        SecurityConfig.class,
        PasswordEncoderConfig.class,
        UsuarioDetailsServiceEmpresa.class,
        ContadorMemoria.class,
        AutorizacaoBO.class,
        LoginAttemptService.class,
        AuthenticationAuditHandler.class,
        JsonAuthenticationEntryPoint.class,
        BruteForceProtectionFilter.class,
        AuditTrailFilter.class
})
@TestPropertySource(properties = {
        // Modo JWT: e' o modo de producao, e o que emite a sessao do
        // usuario. No modo basico o usuario viria do cabecalho, e nao
        // haveria token para o filtro interpretar.
        "app.security.enabled=true",
        "app.security.mode=jwt",
        "app.security.username=admin",
        "app.security.password=SenhaForteDeTeste123",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.max-attempts=5",
        "app.security.rate-limit.max-attempts-per-ip=1000",
        "app.security.rate-limit.window-minutes=15",
        "app.security.trust-proxy=false"
})
class AutorizacaoEndpointTest {

    /** A empresa que vende o leilao 100. */
    private static final Integer EMPRESA_VENDEDORA = 10;

    /** A empresa sem nenhuma relacao com o leilao 100. */
    private static final Integer EMPRESA_ESTRANGEIRA = 30;

    /** O leilao em teste. */
    private static final Integer LEILAO = 100;

    /** O token que o cliente apresenta. */
    private static final String TOKEN = "token.jwt.assinado";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Dublês da camada de negócio.
     *
     * O objetivo aqui NAO e testar a regra de negócio - isso é feito no
     * AutorizacaoBOTest. E' testar se o controller chega a chamar a
     * regra. Por isso o BO e' um duble: se o controller chamar o metodo
     * certo, o duble responde qualquer coisa e o teste so observa o
     * status e a gravacao.
     */
    @MockBean
    private LeilaoBO leilaoBO;

    @MockBean
    private LoteBO loteBO;

    /**
     * Dubles dos repositorios consultados pela AutorizacaoBO.
     */
    @MockBean
    private LeilaoRepository leilaoRepository;

    @MockBean
    private LoteRepository loteRepository;

    @MockBean
    private CompradorRepository compradorRepository;

    @MockBean
    private AuditoriaBO auditoriaBO;

    /**
     * Dublê do servico de tokens.
     *
     * O filtro de JWT valida o token antes de colocar o usuario no
     * contexto. Aqui ele devolve as claims que o teste quiser - e' assim
     * que o teste simula "o usuario X da empresa Y esta autenticado".
     */
    @MockBean
    private TokenJwtBO tokenJwtBO;

    @MockBean
    private TokenRevogadoBO tokenRevogadoBO;

    @BeforeEach
    void preparar() {

        // O leilao 100 e' vendido pela empresa 10.
        when(leilaoRepository.findById(LEILAO)).thenReturn(Optional.of(leilaoDe(LEILAO, EMPRESA_VENDEDORA)));

        // A empresa 10 esta inscrita como compradora? Nao - ela e' a
        // vendedora. Nenhum vinculo de comprador com ela.
        when(compradorRepository.findByIdLeilao(LEILAO)).thenReturn(Collections.<Comprador>emptyList());

        // O lote 1 pertence ao leilao 100.
        when(loteRepository.findById(1)).thenReturn(Optional.of(loteDe(1, LEILAO)));
    }

    // ------------------------------------------------------------------
    // PUT /leiloes - a empresa nao pode transferir o leilao de outra
    // ------------------------------------------------------------------

    /**
     * O ataque principal: uma empresa que vende um leilao tenta
     * transferi-lo para outra empresa.
     *
     * A resposta tem de ser 403, e o leilao nao pode ser gravado.
     */
    @Test
    @DisplayName("PUT /leiloes: a vendedora nao pode transferir o leilao para outra empresa")
    void vendedoraNaoPodeTransferirLeilaoParaOutra() throws Exception {

        autenticarComoEmpresa(EMPRESA_VENDEDORA);

        LeilaoRequest request = new LeilaoRequest();
        request.setCodigo(100);
        request.setDescricao("Leilao com vendedor trocado");
        request.setVendedor(EMPRESA_ESTRANGEIRA);

        mockMvc.perform(put("/leiloes/" + LEILAO)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // O ponto mais importante: o controller nao pode ter gravado
        // nada. Um 403 com gravacao por tras seria um falso positivo -
        // a resposta parece recusada, mas o dado mudou.
        verify(leilaoBO, never()).salvar(any(LeilaoRequest.class), anyInt());
    }

    /**
     * A empresa estrangeira nem chega a tentar alterar o leilao alheio.
     */
    @Test
    @DisplayName("PUT /leiloes: uma empresa sem relacao nao altera o leilao de outra")
    void empresaEstrangeiraNaoAlteraLeilaoDeOutra() throws Exception {

        autenticarComoEmpresa(EMPRESA_ESTRANGEIRA);

        LeilaoRequest request = new LeilaoRequest();
        request.setCodigo(100);
        request.setDescricao("Invasao");
        request.setVendedor(EMPRESA_ESTRANGEIRA);

        mockMvc.perform(put("/leiloes/" + LEILAO)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(leilaoBO, never()).salvar(any(LeilaoRequest.class), anyInt());
    }

    /**
     * O caminho legitimo: a vendedora altera o proprio leilao, mantendo
     * a si como vendedora.
     */
    @Test
    @DisplayName("PUT /leiloes: a vendedora altera o proprio leilao")
    void vendedoraAlteraOProprioLeilao() throws Exception {

        autenticarComoEmpresa(EMPRESA_VENDEDORA);

        LeilaoRequest request = new LeilaoRequest();
        request.setCodigo(100);
        request.setDescricao("Descricao nova");
        request.setVendedor(EMPRESA_VENDEDORA);

        mockMvc.perform(put("/leiloes/" + LEILAO)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        403, result.getResponse().getStatus(),
                        "A vendedora deve poder alterar o proprio leilao."));

        verify(leilaoBO).salvar(any(LeilaoRequest.class), anyInt());
    }

    // ------------------------------------------------------------------
    // PUT /lotes - a empresa nao pode mover o lote para o leilao de outra
    // ------------------------------------------------------------------

    /**
     * O mesmo ataque, no lote: uma empresa tira o lote do leilao do
     * dono e o leva para o leilao dela - ou para o de outra, para
     * proteger o valor inicial de um item que nao e' dela.
     */
    @Test
    @DisplayName("PUT /lotes: a empresa nao pode mover o lote para o leilao de outra")
    void empresaNaoMoveLoteParaLeilaoDeOutra() throws Exception {

        // O leilao 200 e' vendido pela empresa 30.
        when(leilaoRepository.findById(200)).thenReturn(Optional.of(leilaoDe(200, EMPRESA_ESTRANGEIRA)));

        autenticarComoEmpresa(EMPRESA_VENDEDORA);

        LoteRequest request = new LoteRequest();
        request.setNumeroLote(1);
        request.setQuantidade(1);
        request.setValorInicial(new java.math.BigDecimal("1000.00"));
        request.setLeilao(200);

        mockMvc.perform(put("/lotes/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(loteBO, never()).salvar(any(LoteRequest.class), anyInt());
    }

    /**
     * O caminho legitimo: a vendedora mexe no lote do proprio leilao.
     */
    @Test
    @DisplayName("PUT /lotes: a vendedora altera o lote do proprio leilao")
    void vendedoraAlteraLoteDoProprioLeilao() throws Exception {

        autenticarComoEmpresa(EMPRESA_VENDEDORA);

        LoteRequest request = new LoteRequest();
        request.setNumeroLote(1);
        request.setQuantidade(2);
        request.setValorInicial(new java.math.BigDecimal("2500.00"));
        request.setLeilao(LEILAO);

        mockMvc.perform(put("/lotes/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        403, result.getResponse().getStatus(),
                        "A vendedora deve poder mexer no lote do proprio leilao."));

        verify(loteBO).salvar(any(LoteRequest.class), anyInt());
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    /**
     * Faz o filtro de JWT reconhecer o token como pertencente a uma
     * empresa.
     */
    private void autenticarComoEmpresa(Integer empresaId) {

        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("usuario-da-empresa-" + empresaId);
        when(claims.get(TokenJwtBO.CLAIM_FAMILIA)).thenReturn("familia-de-teste");
        when(claims.get(TokenJwtBO.CLAIM_EMPRESA)).thenReturn(empresaId);
        when(claims.get(TokenJwtBO.CLAIM_PAPEL)).thenReturn(Papeis.EMPRESA);

        when(tokenJwtBO.validarTokenAcesso(TOKEN)).thenReturn(claims);
        when(tokenRevogadoBO.familiaRevogada("familia-de-teste")).thenReturn(false);
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
}
