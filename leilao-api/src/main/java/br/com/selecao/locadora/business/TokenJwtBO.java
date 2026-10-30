package br.com.selecao.locadora.business;

import br.com.selecao.locadora.config.ChaveJwtProvider;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emissao e validacao dos tokens JWT.
 *
 * Ha dois tipos de token: o de acesso (vida curta, enviado em toda
 * requisicao) e o de renovacao (vida longa, usado para pedir um acesso
 * novo). Os dois sao assinados com RSA (RS256), o que permite validar a
 * assinatura sem consultar o banco a cada requisicao.
 *
 * A validacao recusa token expirado, de tipo errado, assinado com outra
 * chave ou com emissor desconhecido.
 */
@Component
public class TokenJwtBO {

    private static final Logger LOG = LoggerFactory.getLogger(TokenJwtBO.class);

    /**
     * Tipo do token de acesso.
     */
    public static final String TIPO_ACESSO = "access";

    /**
     * Tipo do token de renovacao.
     */
    public static final String TIPO_RENOVACAO = "refresh";

    /**
     * Leitor de JSON usado para ler o cabecalho do token.
     *
     * E' uma instancia unica e imutavel: o Jackson e' seguro para uso
     * concorrente, entao nao ha necessidade de criar um por chamada.
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Servico que guarda as chaves RSA e sabe qual assina.
     */
    @Autowired
    private ChaveJwtProvider chaveJwtProvider;

    /**
     * Identificacao do emissor dos tokens. Aparece no token e e exigido
     * na validacao - um token emitido por outro sistema (mesmo com
     * assinatura valida) e rejeitado.
     */
    @Value("${app.security.jwt.issuer:az-leilao-api}")
    private String emissor;

    /**
     * Validade do token de acesso.
     */
    @Value("${app.security.jwt.access-token-ttl:PT15M}")
    private Duration validadeTokenAcesso;

    /**
     * Validade do token de renovacao.
     */
    @Value("${app.security.jwt.refresh-token-ttl:P7D}")
    private Duration validadeTokenRenovacao;

    /**
     * Emite um token de acesso.
     *
     * @param usuario identificacao do usuario autenticado
     * @return o token assinado, no formato compacto do JWT
     */
    public String emitirTokenAcesso(String usuario) {
        return emitir(usuario, TIPO_ACESSO, validadeTokenAcesso);
    }

    /**
     * Emite um token de renovacao.
     *
     * @param usuario identificacao do usuario autenticado
     * @return o token assinado, no formato compacto do JWT
     */
    public String emitirTokenRenovacao(String usuario) {
        return emitir(usuario, TIPO_RENOVACAO, validadeTokenRenovacao);
    }

    /**
     * Emite um token do tipo informado.
     */
    private String emitir(String usuario, String tipo, Duration validade) {

        Instant agora = Instant.now();
        Instant expira = agora.plus(validade);

        ChaveJwtProvider.Chave chave = chaveJwtProvider.getChaveAtiva();

        return Jwts.builder()
                .setSubject(usuario)
                .setIssuer(emissor)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(agora))
                .setExpiration(Date.from(expira))
                .claim("typ", tipo)
                // O "kid" no cabecalho e o que permite rotacionar a chave.
                .setHeaderParam("kid", chave.getId())
                .signWith(chave.getPrivada(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Valida um token de ACESSO.
     *
     * @param token o token recebido no cabecalho Authorization
     * @return as claims do token
     * @throws TokenInvalidoException se o token nao for aceitavel
     */
    public Claims validarTokenAcesso(String token) {
        return validar(token, TIPO_ACESSO);
    }

    /**
     * Valida um token de RENOVACAO.
     *
     * @param token o token de renovacao
     * @return as claims do token
     * @throws TokenInvalidoException se o token nao for aceitavel
     */
    public Claims validarTokenRenovacao(String token) {
        return validar(token, TIPO_RENOVACAO);
    }

    /**
     * Valida um token, exigindo que ele seja do tipo esperado.
     *
     * A verificacao acontece em tres etapas, e o ORDEM importa:
     *
     *   1) a assinatura confere? (a chave publica valida)
     *   2) o emissor e o esperado?
     *   3) o tipo e o esperado?
     *
     * A assinatura vem primeiro de proposito: e a verificacao cara e e
     * a unica que garante a integridade. Se ela falhar, nada mais precisa
     * ser checado - e o token inteiro e descartado.
     */
    private Claims validar(String token, String tipoEsperado) {

        if (token == null || token.trim().isEmpty()) {
            throw new TokenInvalidoException("Token vazio");
        }

        try {

            // Descobre qual chave assinou, pelo "kid" do cabecalho.
            String kid = extrairKid(token);

            ChaveJwtProvider.Chave chave = chaveJwtProvider.getChave(kid);

            if (chave == null) {
                // Chave desconhecida: ou o token e de outra aplicacao, ou
                // a chave ja foi rotacionada e o token expirou faz tempo.
                throw new TokenInvalidoException("Chave de assinatura desconhecida: " + kid);
            }

            Claims claims = Jwts.parser()
                    .verifyWith(chave.getPublica())
                    .requireIssuer(emissor)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tipo = claims.get("typ", String.class);

            if (!tipoEsperado.equals(tipo)) {
                throw new TokenInvalidoException(
                        "Tipo de token inesperado. Esperado: " + tipoEsperado + ", recebido: " + tipo);
            }

            return claims;

        } catch (TokenInvalidoException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException cobre: expirado, assinatura invalida, malformado.
            LOG.debug("JWT | Token recusado: {}", e.getMessage());
            throw new TokenInvalidoException("Token invalido ou expirado");
        }
    }

    /**
     * Le o "kid" (key id) do cabecalho do token, SEM validar a assinatura.
     *
     * E o unico ponto do fluxo em que o conteudo do token ainda nao foi
     * verificado. Por isso o valor so e usado para LOCALIZAR a chave -
     * nunca para tomar decisao. A decisao vem sempre da verificacao
     * criptografica, logo abaixo.
     */
    private String extrairKid(String token) {

        try {
            String[] partes = token.split("\\.");

            if (partes.length != 3) {
                throw new TokenInvalidoException("Token malformado");
            }

            String cabecalho = new String(
                    java.util.Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);

            // O cabecalho e um JSON pequeno. Ele e' lido com o Jackson
            // (que ja e' dependencia do projeto, via spring-boot-starter-web)
            // em vez de busca manual de texto: um parser de JSON nao erra
            // com aspas, ordem das chaves ou escapamentos - e um erro nessa
            // leitura faria TODOS os tokens serem recusados.
            com.fasterxml.jackson.databind.JsonNode no =
                    OBJECT_MAPPER.readTree(cabecalho);

            com.fasterxml.jackson.databind.JsonNode kid = no.get("kid");

            if (kid == null || !kid.isTextual() || kid.asText().isEmpty()) {
                throw new TokenInvalidoException("Token sem identificador de chave (kid)");
            }

            return kid.asText();

        } catch (TokenInvalidoException e) {
            throw e;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new TokenInvalidoException("Cabecalho do token ilegivel");
        }
    }

    /**
     * Calcula o hash SHA-256 de um texto, em hexadecimal.
     *
     * E o que permite guardar o token de renovacao NO BANCO sem guardar o
     * token em si: o banco recebe apenas o hash. Se alguem obtiver o
     * conteudo da tabela, nao consegue usar os tokens - porque o valor
     * guardado e o hash, e nao o token. E como uma senha: guardamos o
     * hash, nunca o segredo.
     *
     * @param texto o valor a ser hasheado
     * @return o hash em hexadecimal, com 64 caracteres
     */
    public static String calcularHash(String texto) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(texto.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexadecimal = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hexadecimal.append(String.format("%02x", b));
            }

            return hexadecimal.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel nesta JVM", e);
        }
    }

    /**
     * Validade configurada para o token de acesso.
     */
    public Duration getValidadeTokenAcesso() {
        return validadeTokenAcesso;
    }

    /**
     * Validade configurada para o token de renovacao.
     */
    public Duration getValidadeTokenRenovacao() {
        return validadeTokenRenovacao;
    }
}
