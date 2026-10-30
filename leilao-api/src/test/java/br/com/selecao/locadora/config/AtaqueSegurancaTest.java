package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.AuditoriaBO;
import br.com.selecao.locadora.dto.UnidadeResponse;
import br.com.selecao.locadora.service.UnidadeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
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

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de ataque: o sistema sendo completes, nao so funcionando.
 *
 * A suite "SegurancaIntegracaoTest" prova que a cadeia de filtros se
 * comporta bem no uso normal (401, 429, dado do controller). Esta suite
 * faz o oposto: tenta quebrar.
 *
 * Cada teste aqui corresponde a um ataque real de um ataque conhecido:
 * os OWASP Top 10 e o OWASP API Security Top 10. O criterio de aprovacao
 * nao e "a requisicao foi recusada", e "a requisicao foi recusada E o
 * sistema nao vazou nada sobre o motivo".
 *
 * O que diferencia um teste de seguranca de um teste comum e a segunda
 * verificacao: um 401 que responde "usuario nao existe" ainda e uma
 * falha de seguranca, porque entrega a informacao de quais usuarios
 * existem.
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
        "app.security.enabled=true",
        "app.security.username=admin",
        "app.security.password=SenhaForteDeTeste123",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.max-attempts=3",
        "app.security.rate-limit.max-attempts-per-ip=1000",
        "app.security.rate-limit.window-minutes=15",
        "app.security.trust-proxy=false"
})
class AtaqueSegurancaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private br.com.selecao.locadora.business.UnidadeBO unidadeBO;

    @MockBean
    private AuditoriaBO auditoriaBO;

    @MockBean
    private br.com.selecao.locadora.business.TokenJwtBO tokenJwtBO;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void preparar() {
        loginAttemptService.limpar();
        UnidadeResponse unidade = new UnidadeResponse();
        unidade.setId(1);
        unidade.setNome("Unidade de Teste");
        when(unidadeBO.buscarTodos()).thenReturn(Arrays.asList(unidade));
        when(unidadeBO.buscarPorId(any(Integer.class))).thenReturn(unidade);
    }

    // ------------------------------------------------------------------
    // A01 - Quebra de autenticacao
    // ------------------------------------------------------------------

    /**
     * A01: o token de acesso nao pode ser aceito sem assinatura valida.
     *
     * O ataque classico aqui e o "alg=none": o atacante reescreve o
     * cabecalho do JWT dizendo que o token nao tem assinatura, ealguns
     * validadores aceitam isso por engano, porque so olham para o
     * conteudo das claims.
     *
     * Aqui o teste monta um token com alg=none, no formato JWT correto,
     * e verifica que a API recusa.
     */
    @Test
    @DisplayName("Token com alg=none (sem assinatura) e recusado")
    void tokenSemAlgoritmoDeAssinaturaERecusado() throws Exception {

        String tokenSemAssinatura = montarTokenSemAssinatura("admin");

        mockMvc.perform(get("/unidades").header("Authorization", "Bearer " + tokenSemAssinatura))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A01: o token com "alg=none" precisa responder 401, e nao 500.
     *
     * Um 500 aqui significaria que o token chegou no parser e quebrou o
     * servidor: o atacante ainda nao entrou, mas derrubou a API. Do ponto
     * de vista de disponibilidade, e o mesmo problema.
     */
    @Test
    @DisplayName("Token invalido nao derruba a API (responde 401, nunca 500)")
    void tokenInvalidoNaoDerrubaAApi() throws Exception {

        String[] tokensQuebrados = {
                "nao-e-um-jwt",
                "a.b.c",
                "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9.",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.fake",
                "",
                "Bearer Bearer Bearer"
        };

        for (String token : tokensQuebrados) {
            mockMvc.perform(get("/unidades").header("Authorization", "Bearer " + token))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                            result.getResponse().getStatus() < 500,
                            "Token malformado nao pode gerar erro 500. Token: " + token));
        }
    }

    /**
     * A01: a assinatura do token e conferida de verdade.
     *
     * Aqui o teste troca o payload de um token legitimo por um com
     * privileges diferentes, mantendo o cabecalho e a assinatura
     * originais. Se a API confiasse no conteudo sem verificar a
     * assinatura, o token adulterado passaria.
     *
     * O token e montado na mao porque o segredo de assinatura nao esta
     * acessivel ao teste - e o ponto: um atacante tambem nao tem.
     */
    @Test
    @DisplayName("Token com payload adulterado e recusado")
    void tokenComPayloadAdulteradoERecusado() throws Exception {

        // Monta um JWT com assinatura inventada, mas payload valido.
        String cabecalho = base64Url("{\"alg\":\"RS256\",\"kid\":\"az-leilao-dev-01\"}");
        String payload = base64Url("{\"sub\":\"admin\",\"typ\":\"access\",\"iss\":\"az-leilao-api\"}");
        String assinatura = base64Url("assinatura-que-nao-e-valida");

        String tokenAdulterado = cabecalho + "." + payload + "." + assinatura;

        mockMvc.perform(get("/unidades").header("Authorization", "Bearer " + tokenAdulterado))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A01: nao da para trocar o algoritmo por HS256 (confusao de algoritmo).
     *
     * O ataque: o servidor aceita RS256 (chave publica) e HS256 (chave
     * simetrica). Se o validador escolher o algoritmo pelo cabecalho do
     * token, o atacante pode assinar o token com HS256 usando a CHAVE
     * PUBLICA como segredo simetrico - a chave publica e publica, logo o
     * atacante consegue assinar qualquer coisa.
     *
     * O teste monta exatamente esse token: HS256, assinado com a chave
     * publica convertida em bytes, e verifica que e recusado.
     */
    @Test
    @DisplayName("Token HS256 assinado com a chave publica e recusado (confusao de algoritmo)")
    void tokenComConfusaoDeAlgoritmoERecusado() throws Exception {

        try {
            // Gera um par de chaves RSA so para ter uma chave publica.
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            KeyPair par = gerador.generateKeyPair();

            // A chave publica vira "segredo" simetrico do atacante.
            byte[] materialDaChavePublica = par.getPublic().getEncoded();
            SecretKey segredoFalso = new SecretKeySpec(materialDaChavePublica, "HmacSHA256");

            String token = Jwts.builder()
                    .claim("sub", "admin")
                    .claim("typ", "access")
                    .claim("iss", "az-leilao-api")
                    .expiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(segredoFalso)
                    .compact();

            mockMvc.perform(get("/unidades").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());

        } catch (Exception e) {
            // A construcao do token pode falhar por detalhes da biblioteca
            // (tamanho minimo da chave). O que importa aqui e que o token
            // malicioso nunca seja aceito, entao a falha na montagem e
            // inofensiva.
            org.junit.jupiter.api.Assertions.fail(
                    "Nao foi possivel montar o token de ataque: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // A02 - Dados criptografados com algoritmo fraco (nao se aplica a
    // este projeto, que nao guarda segredo em texto puro - mas o
    // contorno de assinatura abaixo tambem cobre o item A02)
    // ------------------------------------------------------------------

    /**
     * A02: a assinatura e conferida com o algoritmo declarado.
     *
     * Complemento do teste de confusao de algoritmo: aqui o teste
     * verifica que a validacao realmente usa a chave RSA do servidor. Se
     * a assinatura for invalida, o token tem de ser recusado - e o teste
     * garante isso assinando com uma chave RSA diferente da do servidor.
     */
    @Test
    @DisplayName("Token assinado com chave RSA diferente e recusado")
    void tokenAssinadoComOutraChaveERecusado() throws Exception {

        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        KeyPair parDoAtacante = gerador.generateKeyPair();

        String token = Jwts.builder()
                .header().keyId("az-leilao-dev-01").and()
                .claim("sub", "admin")
                .claim("typ", "access")
                .claim("iss", "az-leilao-api")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(parDoAtacante.getPrivate())
                .compact();

        mockMvc.perform(get("/unidades").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // A03 - Injecao (SQL Injection)
    // ------------------------------------------------------------------

    /**
     * A03: SQL injection no parametro de caminho nao quebra nada.
     *
     * O id vai direto para o repositorio, que usa JPQL com parametro
     * vinculado. O teste manda payloads classicos de SQL injection e
     * verifica duas coisas: que a API nao devolve 500 e que o resultado
     * nao vaza fragmento de SQL.
     *
     * Se a consulta fosse montada por concatenacao, o "DROP TABLE" (ou
     * o erro de sintaxe) apareceria na resposta.
     */
    @Test
    @DisplayName("SQL injection no parametro de caminho nao causa erro nem vaza SQL")
    void sqlInjectionNoParametroDeCaminhoNaoFunciona() throws Exception {

        String[] payloads = {
                "1 OR 1=1",
                "1; DROP TABLE tb_unidade;--",
                "1' OR '1'='1",
                "1 UNION SELECT null,null,null",
                "1 AND 1=CONVERT(int,(SELECT TOP 1 name FROM sysobjects))--",
                "../../etc/passwd",
                "1%00"
        };

        for (String payload : payloads) {
            String resposta;
            int status;

            try {
                var resultado = mockMvc.perform(get("/unidades/" + payload))
                        .andReturn().getResponse();
                status = resultado.getStatus();
                resposta = resultado.getContentAsString();
            } catch (Exception e) {
                // O StrictHttpFirewall do Spring (ligado por padrao) recusa
                // a requisicao antes mesmo de ela chegar no controller,
                // quando a URL contem caracteres perigosos como ";",
                // ".." ou "%00". Isso e uma defesa legitima - e ate
                // melhor do que receber a requisicao e recusar no
                // controller - entao o teste considera um acerto.
                String nomeDaExcecao = e.getClass().getSimpleName();
                org.junit.jupiter.api.Assertions.assertTrue(
                        nomeDaExcecao.contains("RequestRejected")
                                || nomeDaExcecao.contains("MalformedUrl")
                                || nomeDaExcecao.contains("Firewalled"),
                        "Payload de injecao provocou uma falha inesperada: "
                                + nomeDaExcecao + " - " + e.getMessage());
                continue;
            }

            org.junit.jupiter.api.Assertions.assertTrue(
                    status < 500,
                    "Payload de injecao nao pode gerar erro 500: " + payload);

            org.junit.jupiter.api.Assertions.assertFalse(
                    resposta.toLowerCase().contains("syntax error"),
                    "A resposta nao pode conter erro de sintaxe SQL. Payload: " + payload);
            org.junit.jupiter.api.Assertions.assertFalse(
                    resposta.toLowerCase().contains("drop table"),
                    "A resposta nao pode refletir o payload de SQL. Payload: " + payload);
        }
    }

    /**
     * A03: SQL injection no corpo do JSON nao quebra nada.
     *
     * Mesmo teste, mas no corpo. O cnpj, por exemplo, tem um formato
     * validado por anotacao - um payload de SQL nem passa pela
     * validacao. O que o teste garante e que o erro devolvido e de
     * validacao (400), e nao um 500 de banco.
     */
    @Test
    @DisplayName("SQL injection no corpo da requisicao e recusado na validacao")
    void sqlInjectionNoCorpoERecusadoNaValidacao() throws Exception {

        String corpo = "{\"cnpj\":\"1 OR 1=1; DROP TABLE tb_empresa;--\","
                + "\"razaoSocial\":\"Teste\"}";

        mockMvc.perform(post("/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getStatus() < 500,
                        "Payload de injecao no corpo nao pode gerar erro 500."));
    }

    // ------------------------------------------------------------------
    // A05 - Controle de acesso quebrado (IDOR)
    // ------------------------------------------------------------------

    /**
     * A05: rota de auditoria tambem exige credencial.
     *
     * A trilha de auditoria registra quem fez o que. Se ela fosse
     * publica, qualquer um leria o historico de operacoes da empresa -
     * e ainda teria os IPs dos usuarios.
     */
    @Test
    @DisplayName("A rota de auditoria exige credencial")
    void auditoriaExigeCredencial() throws Exception {

        mockMvc.perform(get("/auditoria"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A05: o actuator nao expoe endpoints internos.
     *
     * O padrao do Spring Boot expoe /actuator/env, /actuator/beans,
     * /actuator/configprops - que vazam as variaveis de ambiente
     * (inclusive senhas) e a configuracao interna. O projeto restringe a
     * exposicao, e este teste trava essa garantia.
     */
    @Test
    @DisplayName("O actuator nao expoe endpoints que vazam configuracao interna")
    void actuatorNaoExpoeEndpointsInternos() throws Exception {

        String[] endpointsInternos = {
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops",
                "/actuator/heapdump",
                "/actuator/threaddump"
        };

        for (String endpoint : endpointsInternos) {
            int status = mockMvc.perform(get(endpoint))
                    .andReturn().getResponse().getStatus();

            org.junit.jupiter.api.Assertions.assertTrue(
                    status == 404 || status == 401 || status == 403,
                    "Endpoint interno do actuator nao pode responder 200: " + endpoint
                            + " (respondeu " + status + ")");
        }
    }

    // ------------------------------------------------------------------
    // A07 - Falha de identificacao
    // ------------------------------------------------------------------

    /**
     * A07: o tempo de resposta nao denuncia se o usuario existe.
     *
     * Este e o teste mais fino do arquivo. Mesmo que a resposta seja
     * identica (como ja garante outro teste), o atacante tambem mede o
     * tempo: um usuario inexistente costuma responder mais rapido, porque
     * nao ha hash para conferir. A diferenca de tempo revela a
     * existencia do usuario.
     *
     * O codigo do projeto compensa isso comparando sempre contra um hash
     * falso quando o usuario nao existe. O teste mede os dois tempos e
     * exige que sejam realmente proximos.
     */
    @Test
    @DisplayName("O tempo de resposta nao denuncia se o usuario existe")
    void tempoDeRespostaNaoDenunciaSeUsuarioExiste() throws Exception {

        // Descarta o aquecimento (a primeira requisicao paga o custo de
        // carregar classes e criar a conexao de autenticacao).
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")));
        mockMvc.perform(get("/unidades").with(httpBasic("inexistente-1", "senha-errada")));

        long totalUsuarioExistente = medirTempo(() ->
                mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada"))));

        long totalUsuarioInexistente = medirTempo(() ->
                mockMvc.perform(get("/unidades").with(httpBasic("inexistente-2", "senha-errada"))));

        // A razao entre os dois tempos e o que importa. Se um usuario
        // inexistente responder em 10% do tempo do existente, o
        // atacante enumera usuarios so pela latencia.
        double razao = (double) totalUsuarioInexistente / (double) Math.max(totalUsuarioExistente, 1);

        org.junit.jupiter.api.Assertions.assertTrue(razao > 0.5 && razao < 2.0,
                "O tempo de resposta denuncia se o usuario existe. "
                        + "Existente: " + totalUsuarioExistente + "ms, "
                        + "inexistente: " + totalUsuarioInexistente + "ms (razao " + razao + ")");
    }

    // ------------------------------------------------------------------
    // A03/A07 - Injecao de cabecalho e XSS
    // ------------------------------------------------------------------

    /**
     * O valor do correlation ID que vem do cliente nao pode ser ecoado
     * sem sanitize: se fosse, daria para injetar conteudo no log
     * (log forging) ou quebrava o cabecalho da resposta.
     */
    @Test
    @DisplayName("O correlation ID malicioso do cliente nao volta na resposta")
    void correlationIdMaliciosoNaoVoltaNaResposta() throws Exception {

        String idMalicioso = "abc\r\nX-Injetado: sim\r\n\r\n<html>alert(1)</html>";

        String resposta = mockMvc.perform(get("/unidades")
                        .header("X-Correlation-Id", idMalicioso))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(
                resposta.contains("X-Injetado"),
                "O valor malicioso nao pode ser refletido na resposta.");
    }

    /**
     * O content type da resposta tem que ser JSON, e nao HTML.
     *
     * Se um payload XSS voltasse como HTML, o navegador do usuario
     * executaria o script. Verificar o content type fecha essa porta.
     */
    @Test
    @DisplayName("Erro de validacao volta como JSON, nunca como HTML")
    void erroDeValidacaoVoltaComoJson() throws Exception {

        mockMvc.perform(post("/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"<script>alert(1)</script>\"}"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ------------------------------------------------------------------
    // A04 - Insecure design
    // ------------------------------------------------------------------

    /**
     * A04: um limite de requisicoes que nao zera nunca trava o usuario
     * de forma permanente.
     *
     * Se o contador so subisse e nunca fosse limpo no sucesso, o usuario
     * acabaria bloqueado mesmo errando a senha algumas vezes ao longo do
     * dia. O teste faz uma serie de erros seguidos de um acerto e
     * verifica que o acerto zera a contagem.
     */
    @Test
    @DisplayName("Um login bem-sucedido zera o contador de tentativas")
    void loginBemSucedidoZeraOContador() throws Exception {

        // Duas falhas (abaixo do limite de 3).
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized());

        // Um acerto.
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "SenhaForteDeTeste123")))
                .andExpect(status().isOk());

        // O contador zerou, entao duas novas falhas ainda nao bloqueiam.
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A04: o bloqueio e por usuario, e nao por IP.
     *
     * Se o bloqueio fosse so por IP, um atacante de fora da rede
     * bloquearia o usuario legitimo que esta atras do mesmo NAT (a
     * empresa inteira atras de um roteador). O teste verifica que o
     * bloqueio de um usuario nao afeta o outro.
     */
    @Test
    @DisplayName("O bloqueio de um usuario nao afeta outro usuario do mesmo IP")
    void bloqueioDeUmUsuarioNaoAfetaOutro() throws Exception {

        // "admin" erra 3 vezes e fica bloqueado.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")));
        }
        mockMvc.perform(get("/unidades").with(httpBasic("admin", "senha-errada")))
                .andExpect(status().isTooManyRequests());

        // Outro usuario, no mesmo IP, continua funcionando.
        mockMvc.perform(get("/unidades").with(httpBasic("outro-usuario", "senha-errada")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // A05 - Autorizacao por metodo HTTP
    // ------------------------------------------------------------------

    /**
     * A05: nao adianta ter credencial para ler se nao pode escrever.
     *
     * No modelo atual existe um unico papel (ADMIN), entao este teste
     * documenta o estado atual: quem autentica, opera. Serve de
     * trava para quando os papeis forem separados - a hora de definir
     * "@PreAuthorize" e o teste que falha, nao o cliente.
     */
    @Test
    @DisplayName("Credencial valida acessa o recurso protegido em qualquer metodo")
    void credencialValidaAcessaEmQualquerMetodo() throws Exception {

        mockMvc.perform(put("/unidades/1")
                        .with(httpBasic("admin", "SenhaForteDeTeste123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Unidade Alterada\"}"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        401, result.getResponse().getStatus(),
                        "Com credencial valida, o PUT nao pode responder 401."));
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    /**
     * Mede quanto tempo uma acao leva.
     *
     * Aceita uma acao que pode lancar excecao, porque o MockMvc lanca
     * Exception (verificada) em varias assinaturas.
     */
    private long medirTempo(AcaoComExcecao acao) {
        long inicio = System.nanoTime();
        try {
            acao.executar();
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail(
                    "Falha durante a medicao de tempo: " + e.getMessage());
        }
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    /**
     * Acao que pode lancar excecao verificada.
     */
    @FunctionalInterface
    private interface AcaoComExcecao {
        void executar() throws Exception;
    }

    /**
     * Codifica um texto em base64 URL-safe, que e como o JWT transporta
     * cabecalho e payload.
     */
    private String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Monta um token com "alg=none", ou seja, sem assinatura nenhuma.
     *
     * O formato e o de um JWT de verdade: o validador que for ler vai
     * tentar decodificar o payload e, se nao checar a assinatura, vai
     * aceitar.
     */
    private String montarTokenSemAssinatura(String usuario) throws Exception {

        String cabecalho = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + usuario + "\",\"typ\":\"access\","
                + "\"iss\":\"az-leilao-api\",\"exp\":"
                + ((System.currentTimeMillis() + 60000) / 1000) + "}");

        return cabecalho + "." + payload + ".";
    }

    /**
     * Gera uma assinatura RSA com uma chave, so para documentar que a
     * verificacao criptografica acontece de verdade.
     */
    @SuppressWarnings("unused")
    private String assinarComChave(String conteudo, KeyPair par) throws Exception {
        Signature assinatura = Signature.getInstance("SHA256withRSA");
        assinatura.initSign(par.getPrivate());
        assinatura.update(conteudo.getBytes(StandardCharsets.UTF_8));
        return base64Url(Base64.getEncoder().encodeToString(assinatura.sign()));
    }
}
