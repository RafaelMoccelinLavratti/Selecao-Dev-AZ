package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gerenciador das chaves de assinatura dos tokens JWT.
 *
 * O token e assinado com uma chave privada e verificado com a publica.
 * RSA (RS256) e nao HMAC (HS256) porque, no HMAC, quem consegue verificar
 * um token consegue tambem assinar um token falso.
 *
 * As chaves vem de uma pasta (um par de arquivos .pem por identificador) ou,
 * em desenvolvimento, sao geradas em memoria.
 */
@Component
public class ChaveJwtProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ChaveJwtProvider.class);

    /**
     * Algoritmo de assinatura. RS256 = RSA com SHA-256, o unico
     * algoritmo de assinatura "obrigatorio" da RFC 7518 (a RFC exige que
     * todo servidor aceite RS256).
     */
    private static final String ALGORITMO = "RSA";

    /**
     * Tamanho da chave RSA, em bits. 2048 e o minimo aceitavel hoje;
     * a OWASP recomenda 2048 bits ou mais.
     */
    private static final int TAMANHO_CHAVE = 2048;

    /**
     * Pasta com os arquivos de chave. Vazio = gera uma chave efemera.
     */
    @Value("${app.security.jwt.keys-dir:}")
    private String pastaChaves;

    /**
     * Identificador da chave usada para ASSINAR os tokens novos.
     */
    @Value("${app.security.jwt.active-key-id:az-leilao-dev-01}")
    private String idChaveAtiva;

    /**
     * Todas as chaves conhecidas, indexadas pelo "kid".
     *
     * A chave ativa assina; todas validam. E assim que a rotacao
     * funciona sem derrubar token nenhum.
     */
    private final Map<String, Chave> chaves = new LinkedHashMap<>();

    /**
     * Chave usada para assinar (e que assina) no momento.
     */
    private Chave chaveAtiva;

    /**
     * Um par de chaves, com o seu identificador.
     */
    public static final class Chave {

        private final String id;
        private final PrivateKey privada;
        private final PublicKey publica;

        /**
         * Cria um par de chaves.
         *
         * Publico porque e' um objeto de valor legitimo: quem tem um par
         * de chaves RSA pode registra-lo aqui para que a aplicacao o
         * use. E' o que os testes fazem, montando o par em memoria.
         *
         * @param id      identificador da chave (o "kid" gravado no token)
         * @param privada a chave privada (que assina)
         * @param publica a chave publica (que verifica)
         */
        public Chave(String id, PrivateKey privada, PublicKey publica) {
            this.id = id;
            this.privada = privada;
            this.publica = publica;
        }

        public String getId() {
            return id;
        }

        public PrivateKey getPrivada() {
            return privada;
        }

        public PublicKey getPublica() {
            return publica;
        }
    }

    /**
     * Carrega (ou gera) as chaves na inicializacao da aplicacao.
     */
    @PostConstruct
    public void inicializar() {

        if (pastaChaves != null && !pastaChaves.trim().isEmpty()) {
            carregarDaPasta(Paths.get(pastaChaves.trim()));
        } else {
            gerarEfemera();
        }

        // Sem a chave ativa, nada pode ser assinado. E melhor falhar na
        // inicializacao do que descobrir isso no primeiro login.
        if (!chaves.containsKey(idChaveAtiva)) {
            throw new IllegalStateException(
                    "A chave ativa de JWT '" + idChaveAtiva + "' nao foi encontrada. "
                            + "Disponiveis: " + chaves.keySet());
        }

        this.chaveAtiva = chaves.get(idChaveAtiva);

        LOG.info("JWT | Chaves carregadas | ativas={} total={} fonte={}",
                idChaveAtiva, chaves.size(),
                (pastaChaves != null && !pastaChaves.trim().isEmpty()) ? "arquivo" : "efemera");
    }

    /**
     * Carrega as chaves de uma pasta com arquivos PEM.
     *
     * Cada par de arquivos com o mesmo nome forma uma chave:
     *     az-leilao-2026-01.private.pem
     *     az-leilao-2026-01.public.pem
     */
    private void carregarDaPasta(Path pasta) {

        if (!Files.isDirectory(pasta)) {
            throw new IllegalStateException("A pasta de chaves JWT nao existe: " + pasta);
        }

        try (Stream<Path> arquivos = Files.list(pasta)) {

            List<Path> privados = arquivos
                    .filter(p -> p.getFileName().toString().endsWith(".private.pem"))
                    .sorted()
                    .collect(Collectors.toList());

            for (Path arquivoPrivado : privados) {

                String nome = arquivoPrivado.getFileName().toString();
                String id = nome.substring(0, nome.length() - ".private.pem".length());

                Path arquivoPublico = pasta.resolve(id + ".public.pem");

                if (!Files.exists(arquivoPublico)) {
                    throw new IllegalStateException(
                            "A chave '" + id + "' tem o arquivo privado mas nao tem o publico: "
                                    + arquivoPublico);
                }

                PrivateKey privada = lerPrivada(arquivoPrivado);
                PublicKey publica = lerPublica(arquivoPublico);

                chaves.put(id, new Chave(id, privada, publica));
            }

        } catch (IOException e) {
            throw new IllegalStateException("Falha ao listar as chaves JWT em " + pasta, e);
        }

        if (chaves.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhuma chave JWT encontrada em " + pasta + " (esperado <id>.private.pem).");
        }
    }

    /**
     * Gera um par de chaves efemero, valido apenas enquanto o processo
     * estiver de pe.
     */
    private void gerarEfemera() {

        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance(ALGORITMO);
            gerador.initialize(TAMANHO_CHAVE);
            KeyPair par = gerador.generateKeyPair();

            chaves.put(idChaveAtiva, new Chave(idChaveAtiva, par.getPrivate(), par.getPublic()));

            LOG.warn("JWT | ATENCAO: chave RSA gerada em memoria (nenhuma pasta de chaves "
                            + "configurada). Os tokens emitidos serao invalidados a cada "
                            + "reinicio da aplicacao. Em producao, configure "
                            + "'app.security.jwt.keys-dir'.");

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA nao disponivel nesta JVM", e);
        }
    }

    /**
     * Le a chave privada de um arquivo PEM no formato PKCS#8.
     */
    private PrivateKey lerPrivada(Path arquivo) {

        String conteudo = lerArquivo(arquivo);

        try {
            byte[] conteudoDecodificado = Base64.getDecoder().decode(conteudo);
            return KeyFactory.getInstance(ALGORITMO).generatePrivate(new PKCS8EncodedKeySpec(conteudoDecodificado));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Chave privada JWT invalida em " + arquivo, e);
        }
    }

    /**
     * Le a chave publica de um arquivo PEM no formato X.509.
     */
    private PublicKey lerPublica(Path arquivo) {

        String conteudo = lerArquivo(arquivo);

        try {
            byte[] conteudoDecodificado = Base64.getDecoder().decode(conteudo);
            return KeyFactory.getInstance(ALGORITMO).generatePublic(new X509EncodedKeySpec(conteudoDecodificado));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Chave publica JWT invalida em " + arquivo, e);
        }
    }

    /**
     * Le o arquivo, removendo o cabecalho e o rodape do PEM e todo
     * caractere em branco (o PEM quebra a chave em varias linhas).
     */
    private String lerArquivo(Path arquivo) {

        try {
            String bruto = new String(Files.readAllBytes(arquivo), StandardCharsets.UTF_8);

            return bruto
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o arquivo de chave JWT " + arquivo, e);
        }
    }

    /**
     * Devolve a chave que assina os tokens novos.
     */
    public Chave getChaveAtiva() {
        return chaveAtiva;
    }

    /**
     * Procura uma chave pelo identificador ("kid").
     *
     * @param id identificador da chave
     * @return a chave, ou null se nao for conhecida
     */
    public Chave getChave(String id) {
        return chaves.get(id);
    }

    /**
     * Todos os identificadores de chave conhecidos.
     */
    public Map<String, Chave> getChaves() {
        return Collections.unmodifiableMap(new HashMap<>(chaves));
    }
}
