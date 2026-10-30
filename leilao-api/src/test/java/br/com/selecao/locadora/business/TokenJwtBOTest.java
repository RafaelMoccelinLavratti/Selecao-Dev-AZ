package br.com.selecao.locadora.business;

import br.com.selecao.locadora.config.ChaveJwtProvider;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de emissao e validacao dos tokens JWT.
 *
 * A assinatura e uma operacao puramente criptografica: o teste gera um par
 * de chaves RSA em memoria e valida os tokens contra ele, sem tocar no
 * banco.
 *
 * Os testes cobrem token adulterado, expirado, de tipo errado e assinado com
 * outra chave.
 */
class TokenJwtBOTest {

    /**
     * Negocio sob teste.
     */
    private TokenJwtBO tokenJwtBO;

    /**
     * Provedor de chaves, com um par RSA gerado em memoria.
     */
    private ChaveJwtProvider chaveJwtProvider;

    /**
     * Par de chaves usado nos testes.
     */
    private KeyPair parDeChaves;

    @BeforeEach
    void preparar() throws Exception {

        // Gera um par RSA 2048 para o teste (assimetrico, como em producao).
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        parDeChaves = gerador.generateKeyPair();

        chaveJwtProvider = new ChaveJwtProvider();
        ChaveJwtProvider.Chave chave = new ChaveJwtProvider.Chave(
                "chave-de-teste", parDeChaves.getPrivate(), parDeChaves.getPublic());

        ReflectionTestUtils.setField(chaveJwtProvider, "chaves",
                new java.util.LinkedHashMap<String, ChaveJwtProvider.Chave>() {{
                    put("chave-de-teste", chave);
                }});
        ReflectionTestUtils.setField(chaveJwtProvider, "chaveAtiva", chave);

        tokenJwtBO = new TokenJwtBO();
        ReflectionTestUtils.setField(tokenJwtBO, "chaveJwtProvider", chaveJwtProvider);
        ReflectionTestUtils.setField(tokenJwtBO, "emissor", "az-leilao-api-teste");
        ReflectionTestUtils.setField(tokenJwtBO, "validadeTokenAcesso", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(tokenJwtBO, "validadeTokenRenovacao", Duration.ofDays(7));
    }

    /**
     * Teste 1: o token emitido e valido e traz o usuario correto.
     */
    @Test
    @DisplayName("O token emitido e valido e traz o usuario correto")
    void tokenEmitidoEValido() {

        String token = tokenJwtBO.emitirTokenAcesso("admin");

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3,
                "um JWT tem 3 partes separadas por ponto (cabecalho, corpo, assinatura)");

        Claims claims = tokenJwtBO.validarTokenAcesso(token);

        assertEquals("admin", claims.getSubject());
        assertEquals("az-leilao-api-teste", claims.getIssuer());
        assertEquals(TokenJwtBO.TIPO_ACESSO, claims.get("typ"));
    }

    /**
     * Teste 2: um token adulterado nao passa.
     *
     * O atacante pega um token valido e troca o usuario no corpo. Como a
     * assinatura cobre o corpo inteiro, a assinatura deixa de bater - e o
     * token e recusado.
     */
    @Test
    @DisplayName("Um token adulterado nao passa na validacao")
    void tokenAdulteradoERecusado() {

        String token = tokenJwtBO.emitirTokenAcesso("admin");

        // Troca "admin" por "atacante" no corpo (a segunda parte do JWT).
        String[] partes = token.split("\\.");
        String corpoAdulterado = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"atacante\",\"iss\":\"az-leilao-api-teste\"}")
                        .getBytes(StandardCharsets.UTF_8));

        String tokenAdulterado = partes[0] + "." + corpoAdulterado + "." + partes[2];

        assertThrows(TokenInvalidoException.class,
                () -> tokenJwtBO.validarTokenAcesso(tokenAdulterado),
                "um token com o corpo alterado deve ser recusado");
    }

    /**
     * Teste 3: um token vencido nao passa.
     */
    @Test
    @DisplayName("Um token expirado nao passa na validacao")
    void tokenExpiradoERecusado() {

        // Emite um token que ja nasce vencido (validade negativa).
        String token = emitirComValidade("admin", TokenJwtBO.TIPO_ACESSO, Duration.ofSeconds(-10));

        assertThrows(TokenInvalidoException.class,
                () -> tokenJwtBO.validarTokenAcesso(token),
                "um token expirado deve ser recusado");
    }

    /**
     * Teste 4: um token de renovacao nao vale como token de acesso.
     *
     * Este teste prova a separacao dos dois tipos de token. Se um token de
     * renovacao (valido por 7 dias) fosse aceito como token de acesso, o
     * atacante que tivesse acesso a um token de renovacao - que trafega
     * muito menos e vive mais tempo - teria acesso completo e duradouro
     * a API.
     */
    @Test
    @DisplayName("Um token de renovacao nao vale como token de acesso")
    void tokenDeRenovacaoNaoValeComoAcesso() {

        String tokenRenovacao = tokenJwtBO.emitirTokenRenovacao("admin");

        assertThrows(TokenInvalidoException.class,
                () -> tokenJwtBO.validarTokenAcesso(tokenRenovacao),
                "o token de renovacao nao pode ser usado como token de acesso");

        // Mas ele e valido como token de renovacao.
        Claims claims = tokenJwtBO.validarTokenRenovacao(tokenRenovacao);
        assertEquals("admin", claims.getSubject());
        assertEquals(TokenJwtBO.TIPO_RENOVACAO, claims.get("typ"));
    }

    /**
     * Teste 5: um token com a chave errada nao passa.
     */
    @Test
    @DisplayName("Um token assinado com outra chave nao passa")
    void tokenComOutraChaveERecusado() throws Exception {

        // Gera um segundo par de chaves, completamente diferente.
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        KeyPair outroPar = gerador.generateKeyPair();

        String token = Jwts.builder()
                .setSubject("atacante")
                .setIssuer("az-leilao-api-teste")
                .claim("typ", TokenJwtBO.TIPO_ACESSO)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 900_000))
                .signWith(outroPar.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(TokenInvalidoException.class,
                () -> tokenJwtBO.validarTokenAcesso(token),
                "um token assinado com outra chave deve ser recusado");
    }

    /**
     * Teste 6: o token carrega o "kid" (identificador da chave).
     *
     * E o que permite rotacionar chaves sem derrubar ninguem: a aplicacao
     * mantem a chave antiga (ainda valida) e a nova (ja assina), e cada
     * token e conferido com a chave que o assinou.
     */
    @Test
    @DisplayName("O token carrega o identificador da chave (kid)")
    void tokenCarregaOIdentificadorDaChave() {

        String token = tokenJwtBO.emitirTokenAcesso("admin");

        String cabecalho = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                StandardCharsets.UTF_8);

        assertTrue(cabecalho.contains("\"kid\":\"chave-de-teste\""),
                "o cabecalho do token deve carregar o kid da chave: " + cabecalho);
    }

    /**
     * Teste 7: os dois tokens de um mesmo usuario sao diferentes.
     *
     * Porque cada emissao sorteia um "jti" (identificador unico) e um
     * instante de emissao proprio. Sem isso, o mesmo usuario teria sempre
     * o mesmo token - e a revogacao de um token especifico seria impossivel.
     */
    @Test
    @DisplayName("Dois tokens do mesmo usuario sao diferentes")
    void tokensDoMesmoUsuarioSaoDiferentes() {

        String primeiro = tokenJwtBO.emitirTokenAcesso("admin");
        String segundo = tokenJwtBO.emitirTokenAcesso("admin");

        assertNotEquals(primeiro, segundo,
                "cada emissao precisa gerar um token diferente");
    }

    /**
     * Teste 8: o hash do token e estavel e tem 64 caracteres.
     *
     * O hash SHA-256 e o que e gravado no banco no lugar do token. Ele
     * precisa ser deterministico (o mesmo token sempre gera o mesmo hash
     * - senao a busca no banco nunca encontraria) e ter tamanho fixo.
     */
    @Test
    @DisplayName("O hash do token e estavel e tem 64 caracteres")
    void hashDoTokenEEstavel() {

        String token = tokenJwtBO.emitirTokenAcesso("admin");

        String hash1 = TokenJwtBO.calcularHash(token);
        String hash2 = TokenJwtBO.calcularHash(token);

        assertEquals(hash1, hash2, "o hash do mesmo token deve ser sempre igual");
        assertTrue(hash1.length() == 64, "o SHA-256 em hexadecimal tem 64 caracteres");
        assertFalse(hash1.contains(token), "o hash nao pode conter o token em texto puro");
    }

    /**
     * Emite um token com uma validade especifica (usado para testar expiracao).
     */
    private String emitirComValidade(String usuario, String tipo, Duration validade) {

        java.util.Date emissao = new java.util.Date(System.currentTimeMillis() + validade.toMillis());
        java.util.Date expiracao = new java.util.Date(emissao.getTime() + 1000);

        return Jwts.builder()
                .setSubject(usuario)
                .setIssuer("az-leilao-api-teste")
                .claim("typ", tipo)
                .setIssuedAt(emissao)
                .setExpiration(expiracao)
                .setHeaderParam("kid", "chave-de-teste")
                .signWith(parDeChaves.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
