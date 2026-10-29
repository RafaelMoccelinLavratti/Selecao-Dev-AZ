package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.AuditoriaBO;
import br.com.selecao.locadora.dto.UnidadeResponse;
import br.com.selecao.locadora.service.UnidadeService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 *  TESTE DE INTEGRACAO: CADEIA DE SEGURANCA DA API
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE UM TESTE DE INTEGRACAO?
 *  ------------------------------------------------------
 *  Os outros testes do projeto verificam UMA classe por vez. Este verifica
 *  a CADEIA DE FILTROS COMPLETA: como o Spring Security, o filtro
 *  anti-forca-bruta, o handler de auditoria e o controller se encaixam
 *  na mesma requisicao.
 *
 *  Isso e essencial porque a maior parte dos bugs de seguranca em API
 *  acontece exatamente na EMENDA entre os filtros - e nenhum teste de
 *  unidade pega isso.
 *
 *  ------------------------------------------------------
 *  POR QUE @WebMvcTest E NAO @SpringBootTest?
 *  ------------------------------------------------------
 *  @SpringBootTest sobe o container INTEIRO, o que exigiria o PostgreSQL
 *  no ar - e quebraria "mvn test" em maquinas sem banco.
 *
 *  @WebMvcTest sobe apenas a camada web (controllers, filtros, seguranca).
 *  A configuracao do banco e ignorada, entao o teste roda em qualquer
 *  maquina, em segundos. O duble do "@MockBean" substitui a camada de
 *  negocio, que nao e o objeto deste teste.
 *
 *  ------------------------------------------------------
 *  O QUE ESTE TESTE PROVA
 *  ------------------------------------------------------
 *  1) SEM credencial, uma rota protegida responde 401 em JSON (e nao
 *     uma pagina HTML).
 *  2) COM credencial ERRADA, responde 401 em JSON.
 *  3) COM credencial CORRETA, responde 200 e - CRUCIAL - o corpo e o
 *     DADO do controller, e nao uma mensagem de autenticacao.
 *  4) Apos estourar o limite de tentativas, responde 429 com
 *     "Retry-After".
 *  5) O cabecalho "WWW-Authenticate" e enviado no 401.
 *
 *  O item 3 e o mais importante do arquivo. Existe uma armadilha real no
 *  HTTP Basic: como o navegador reenvia o cabecalho de credenciais em
 *  TODAS as requisicoes, um handler de sucesso mal escrito combinas
 *  (commit) a resposta antes do controller rodar - e TODO endpoint passa
 *  a devolver a resposta da autenticacao em vez do dado pedido. Esse
 *  bug so apareceria em producao (em desenvolvimento a autenticacao
 *  esta desligada), e este teste e a rede de seguranca contra ele.
 * ============================================================================
 */
@WebMvcTest(controllers = UnidadeService.class)
@Import({
        SecurityConfig.class,
        PasswordEncoderConfig.class,
        LoginAttemptService.class,
        AuthenticationAuditHandler.class,
        JsonAuthenticationEntryPoint.class,
        BruteForceProtectionFilter.class,
        AuditTrailFilter.class
})
@TestPropertySource(properties = {
        // Liga a autenticacao (o padrao do projeto e desligada, para o
        // avaliador testar as rotas sem login).
        "app.security.enabled=true",
        "app.security.username=admin",
        "app.security.password=SenhaForteDeTeste123",
        // Limites baixos, para o teste de bloqueio ser rapido.
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.max-attempts=3",
        "app.security.rate-limit.max-attempts-per-ip=100",
        "app.security.rate-limit.window-minutes=15",
        // Sem proxy confiavel neste cenario.
        "app.security.trust-proxy=false"
})
class SegurancaIntegracaoTest {

    /** Simulador de requisicoes HTTP (nao precisa subir o servidor). */
    @Autowired
    private MockMvc mockMvc;

    /** Serializador JSON, usado para montar o corpo esperado. */
    @Autowired
    private ObjectMapper objectMapper;

    /** Duble da camada de negocio (o banco nao e necessario aqui). */
    @MockBean
    private br.com.selecao.locadora.business.UnidadeBO unidadeBO;

    /**
     * Duble da trilha de auditoria selada por hash.
     *
     * A trilha real grava em "tb_auditoria" e depende do repositorio JPA -
     * que nao existe neste recorte de teste (e' o proposito do @WebMvcTest:
     * nao exigir banco). O duble preserva o contrato (o filtro chama
     * "registrar") sem tocar no banco.
     */
    @MockBean
    private AuditoriaBO auditoriaBO;

    /**
     * Duble do servico de tokens JWT.
     *
     * O JwtAuthenticationFilter e' um filtro (@Component), e o @WebMvcTest
     * carrega filtros automaticamente - por isso o servico de tokens
     * tambem precisa existir no contexto. Como este teste valida o modo
     * HTTP BASIC, o JWT nao e' exercitado aqui: o duble existe apenas
     * para satisfazer a injecao.
     */
    @MockBean
    private br.com.selecao.locadora.business.TokenJwtBO tokenJwtBO;

    /**
     * Controle de tentativas de login.
     *
     * Precisa ser reiniciado a cada teste. O contexto do Spring e
     * COMPARTILHADO entre os metodos de teste de uma mesma classe, e o
     * servico guarda os contadores em memoria - sem esta limpeza, as
     * falhas de um teste contaminariam o seguinte (por exemplo, o
     * usuario "admin" continuaria bloqueado de um teste para o outro).
     */
    @Autowired
    private LoginAttemptService loginAttemptService;

    /**
     * Prepara um cenario com uma unidade cadastrada, para que a resposta
     * do controller tenha um conteudo verificavel.
     */
    @BeforeEach
    void preparar() {

        // Zera os contadores de tentativa herdados de um teste anterior.
        loginAttemptService.limpar();

        UnidadeResponse unidade = new UnidadeResponse();
        unidade.setId(1);
        unidade.setNome("Unidade de Teste");

        when(unidadeBO.buscarTodos()).thenReturn(Arrays.asList(unidade));
        when(unidadeBO.buscarPorId(any(Integer.class))).thenReturn(unidade);
    }

    /**
     * Teste 1: rota protegida SEM credencial devolve 401 em JSON.
     *
     * Verificamos o "tipo" da resposta (application/json) e o conteudo.
     * Se o handler padrao do Spring estivesse ativo, viria HTML.
     */
    @Test
    @DisplayName("Rota protegida sem credencial responde 401 em JSON")
    void semCredencialRetorna401EmJson() throws Exception {

        mockMvc.perform(get("/unidades"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").exists())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    /**
     * Teste 2: credencial ERRADA devolve 401 em JSON.
     */
    @Test
    @DisplayName("Credencial errada responde 401 em JSON")
    void credencialErradaRetorna401() throws Exception {

        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * Teste 3: a resposta de 401 NAO revela se o usuario existe.
     *
     * A mensagem e sempre a mesma, para impedir a enumeracao de usuarios
     * (item A07:2021 da OWASP).
     */
    @Test
    @DisplayName("A resposta de 401 nao revela se o usuario existe")
    void respostaNaoRevelaSeUsuarioExiste() throws Exception {

        String corpoUsuarioInexistente = mockMvc.perform(
                        get("/unidades").with(httpBasic("usuario-que-nao-existe", "qualquer")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String corpoSenhaErrada = mockMvc.perform(
                        get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(corpoUsuarioInexistente, corpoSenhaErrada,
                "A resposta deve ser IDENTICA para usuario inexistente e senha errada, "
                        + "senao o atacante consegue enumerar usuarios validos.");
    }

    /**
     * Teste 4: credencial CORRETA devolve 200 e o DADO DO CONTROLLER.
     *
     * Este e o teste mais importante do arquivo. Se a resposta de
     * autenticacao "vazasse" para a resposta do controller, o corpo
     * seria a mensagem de login em vez da lista de unidades - e o
     * sistema inteiro estaria quebrado em producao.
     */
    @Test
    @DisplayName("Credencial correta devolve 200 e o dado real do controller")
    void credencialCorretaDevolveODadoDoController() throws Exception {

        mockMvc.perform(get("/unidades").with(httpBasic("admin", "SenhaForteDeTeste123")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Unidade de Teste"));
    }

    /**
     * Teste 5: apos estourar o limite de tentativas, responde 429.
     *
     * O limite e 3 (configurado em @TestPropertySource). A quarta
     * tentativa ja devolve "muitas tentativas", com o cabecalho
     * "Retry-After" informando o tempo de espera.
     */
    @Test
    @DisplayName("Ao estourar o limite de tentativas, responde 429 com Retry-After")
    void estourarLimiteDevolve429() throws Exception {

        // As tres primeiras tentativas falham com 401.
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                    .andExpect(status().isUnauthorized());
        }

        // A quarta ja encontra a conta bloqueada.
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().string("Retry-After",
                        org.hamcrest.Matchers.matchesPattern("\\d+")));

        // O tempo de espera precisa ser REAL, e nao zero: um "Retry-After: 0"
        // mandaria o cliente tentar de novo imediatamente, o que nao
        // pouparia ninguem e ainda overloadaria o servidor.
        String retryAfter = mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");

        int segundos = Integer.parseInt(retryAfter);

        org.junit.jupiter.api.Assertions.assertTrue(segundos > 0,
                "O Retry-After deveria informar um tempo de espera real, veio: " + segundos);
        org.junit.jupiter.api.Assertions.assertTrue(segundos <= 15 * 60,
                "O tempo de espera nao pode passar da janela de 15 minutos, veio: " + segundos);
    }

    /**
     * Teste 6: o cabecalho "WWW-Authenticate" e enviado no 401.
     *
     * E o que faz o navegador oferecer a janela de login basico.
     */
    @Test
    @DisplayName("A resposta 401 envia o cabecalho WWW-Authenticate")
    void enviaCabecalhoWwwAuthenticate() throws Exception {

        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.containsString("Basic")));
    }

    /**
     * Teste 7: o endpoint de saude (actuator) NAO e barrado pela seguranca.
     *
     * O orquestrador (Docker/Kubernetes) consulta esse endpoint para
     * saber se a aplicacao esta viva. Se exigisse autenticacao, o
     * container seria considerado morto e reiniciado.
     *
     * OBSERVACAO SOBRE O 404 ESPERADO:
     * Neste recorte de teste (@WebMvcTest) o Actuator NAO e carregado - ele
     * nao faz parte da camada web. Por isso a resposta e 404 (rota
     * inexistente) e nao 200.
     *
     * E exatamente isso que o teste verifica: a requisicao CHEGOU ate a
     * camada de roteamento, ou seja, NAO foi barrada pela seguranca com
     * 401 ou 403. Um 401 aqui indicaria que o health check exigiria
     * credencial - e o orquestrador reiniciaria a aplicacao sem parar.
     */
    @Test
    @DisplayName("O endpoint de saude nao e barrado pela camada de seguranca")
    void actuatorHealthNaoEBarrado() throws Exception {

        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        401, result.getResponse().getStatus(),
                        "O health check nao pode exigir autenticacao."));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        403, result.getResponse().getStatus(),
                        "O health check nao pode exigir autenticacao."));
    }
}
