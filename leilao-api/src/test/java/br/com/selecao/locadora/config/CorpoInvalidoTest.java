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
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.repository.CompradorRepository;
import br.com.selecao.locadora.repository.LeilaoRepository;
import br.com.selecao.locadora.repository.LoteRepository;
import br.com.selecao.locadora.service.LeilaoService;
import br.com.selecao.locadora.service.LoteService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
 * Corpo da requisicao com tipo de dado errado -> HTTP 400, e nao 500.
 *
 * O cenario: o campo "vendedor" do leilao e' o ID da empresa (um
 * inteiro). Se o cliente mandar a razao social no lugar do id, o
 * Jackson falha ao converter e lanca a excecao de conversao - antes do
 * controller, antes da regra de negocio.
 *
 * Sem tratamento especifico, essa excecao cai no handler generico e
 * volta como 500. E' a resposta errada por dois motivos:
 *
 *   1. Nao foi erro do servidor - foi o cliente mandando dado invalido.
 *      Um 500 faz o cliente repetir a mesma requisicao, que vai falhar
 *      igual.
 *   2. O 500 nao diz QUAL campo foi recusado. O cliente descobre o
 *      problema so olhando o log do servidor.
 *
 * Este teste existe para travar esse comportamento: se alguem mexer no
 * tratamento global e o 500 voltar, o teste falha.
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
class CorpoInvalidoTest {

    /** A empresa que vende o leilao 100. */
    private static final Integer EMPRESA_VENDEDORA = 10;

    /** O leilao em teste. */
    private static final Integer LEILAO = 100;

    /** O token que o cliente apresenta. */
    private static final String TOKEN = "token.jwt.assinado";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeilaoBO leilaoBO;

    @MockBean
    private LoteBO loteBO;

    @MockBean
    private CompradorRepository compradorRepository;

    @MockBean
    private LeilaoRepository leilaoRepository;

    @MockBean
    private LoteRepository loteRepository;

    @MockBean
    private TokenJwtBO tokenJwtBO;

    @MockBean
    private TokenRevogadoBO tokenRevogadoBO;

    @MockBean
    private AuditoriaBO auditoriaBO;

    @BeforeEach
    void preparar() {
        // O token do cliente pertence a empresa 10, que e' a vendedora do
        // leilao 100.
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("usuario-da-empresa-" + EMPRESA_VENDEDORA);
        when(claims.get(TokenJwtBO.CLAIM_FAMILIA)).thenReturn("familia-de-teste");
        when(claims.get(TokenJwtBO.CLAIM_EMPRESA)).thenReturn(EMPRESA_VENDEDORA);
        when(claims.get(TokenJwtBO.CLAIM_PAPEL)).thenReturn(Papeis.EMPRESA);
        when(tokenJwtBO.validarTokenAcesso(TOKEN)).thenReturn(claims);
        when(tokenRevogadoBO.familiaRevogada("familia-de-teste")).thenReturn(false);

        // Na entidade, o "vendedor" e' o ManyToOne da empresa vendedora.
        // O DTO, esse sim, leva o ID (um inteiro).
        Empresa vendedora = new Empresa();
        vendedora.setId(EMPRESA_VENDEDORA);

        Leilao leilao = new Leilao();
        leilao.setId(LEILAO);
        leilao.setCodigo(100);
        leilao.setDescricao("Leilao de maquinas industriais");
        leilao.setVendedor(vendedora);
        when(leilaoRepository.findById(LEILAO)).thenReturn(Optional.of(leilao));

        when(compradorRepository.findByIdLeilao(LEILAO)).thenReturn(Collections.emptyList());
    }

    /**
     * O "vendedor" chega como texto onde o DTO espera inteiro.
     */
    @Test
    @DisplayName("campo com tipo errado no corpo devolve 400, e nao 500")
    void vendedorComoTextoDevolve400() throws Exception {
        String corpo = "{\"codigo\":100,\"descricao\":\"Leilao de maquinas Horizontais\","
                + "\"vendedor\":\"Metalurgica Aurora Ltda\"}";

        mockMvc.perform(put("/leiloes/" + LEILAO)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").exists())
                .andExpect(jsonPath("$.mensagem").exists());

        // E o mais importante: nada foi gravado.
        verify(leilaoBO, never()).salvar(any(LeilaoRequest.class), anyInt());
    }

    /**
     * A mensagem diz QUAL campo falhou - e sem as aspas que o Jackson
     * coloca.
     */
    @Test
    @DisplayName("a mensagem de 400 diz qual campo foi recusado")
    void mensagemNomeiaOCampo() throws Exception {
        String corpo = "{\"codigo\":100,\"descricao\":\"Teste\","
                + "\"vendedor\":\"Metalurgica Aurora Ltda\"}";

        mockMvc.perform(put("/leiloes/" + LEILAO)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("vendedor")))
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\""))));
    }

    /**
     * O mesmo para o lote: "leilao" e' inteiro, e um texto no lugar
     * causa o mesmo desvio.
     */
    @Test
    @DisplayName("campo com tipo errado no lote tambem devolve 400")
    void leilaoComoTextoDevolve400() throws Exception {
        String corpo = "{\"quantidade\":10,\"valorInicial\":500.00,"
                + "\"unidade\":\"Galpao\",\"leilao\":\"Leilao de maquinas\"}";

        mockMvc.perform(put("/lotes/1")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(loteBO, never()).salvar(any(LoteRequest.class), anyInt());
    }
}
