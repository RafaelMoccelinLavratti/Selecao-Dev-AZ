package br.com.selecao.locadora;

import br.com.selecao.locadora.config.PasswordEncoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 *  UTILITARIO: GERADOR E VERIFICADOR DE HASH DE SENHA
 * ============================================================================
 *
 *  ESTE TESTE FAZ DUAS COISAS:
 *
 *  1) GERA os hashes BCrypt das senhas de teste ("Senha@1" ... "Senha@12"),
 *     para uso no script de massa de dados (sql/02_dml.sql). Como o banco
 *     guarda apenas o HASH, e nao a senha, o script de dados precisa
 *     conter os hashes ja prontos.
 *
 *  2) VERIFICA que os hashes que estao no script 02_dml.sql conferem com
 *     as senhas "Senha@1" ... "Senha@12". Isso garante que a massa de
 *     dados de teste fica coerente - se alguem editar o script e trocar um
 *     hash por outro, este teste avisa.
 *
 *  COMO RODAR:
 *      mvn test -Dtest=GeradorHashSenhaTest
 *
 *  SOBRE O BCrypt: como ele sorteia um "salt" novo a cada execucao, rodar
 *  este teste duas vezes produz hashes diferentes (e igualmente validos)
 *  para a mesma senha. Esse e o comportamento correto do algoritmo - e e
 *  por isso que dois usuarios com a mesma senha nunca ficam com o mesmo
 *  hash no banco.
 * ============================================================================
 */
class GeradorHashSenhaTest {

    /** Caminho do script de massa de dados, relativo a pasta do projeto. */
    private static final Path SCRIPT_DML = Paths.get(
            "src", "main", "resources", "sql", "02_dml.sql");

    @Test
    @DisplayName("Gera os hashes BCrypt das senhas de teste (Senha@1 ... Senha@12)")
    void gerarHashes() {
        PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

        System.out.println();
        System.out.println("=== HASHES BCrypt PARA A MASSA DE DADOS ===");

        for (int i = 1; i <= 12; i++) {
            System.out.println("Senha@" + i + " -> " + encoder.encode("Senha@" + i));
        }

        System.out.println("=== FIM ===");
        System.out.println();
    }

    /**
     * Le os hashes gravados no script de massa de dados e confere se
     * cada um corresponde a senha "Senha@&lt;posicao&gt;".
     */
    @Test
    @DisplayName("Os hashes do script 02_dml.sql conferem com as senhas de teste")
    void verificarHashesDoScriptDeDados() throws IOException {

        PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

        // Extrai todos os hashes (valores entre aspas que comecam com $2a$).
        String conteudo = new String(Files.readAllBytes(SCRIPT_DML), StandardCharsets.UTF_8);
        Pattern padrao = Pattern.compile("'\\$2a\\$10\\$[^']+'");
        Matcher matcher = padrao.matcher(conteudo);

        List<String> hashes = new ArrayList<>();
        while (matcher.find()) {
            hashes.add(matcher.group().replace("'", ""));
        }

        // O enunciado pede pelo menos 10 registros por tabela; sao 12 empresas.
        assertTrue(hashes.size() >= 10,
                "o script de dados deve ter pelo menos 10 hashes, encontrou " + hashes.size());

        // Cada hash deve validar a senha "Senha@" + (indice + 1).
        for (int i = 0; i < hashes.size(); i++) {
            String senhaEsperada = "Senha@" + (i + 1);
            assertTrue(encoder.matches(senhaEsperada, hashes.get(i)),
                    "o hash da posicao " + (i + 1) + " nao corresponde a senha " + senhaEsperada);
        }
    }
}
