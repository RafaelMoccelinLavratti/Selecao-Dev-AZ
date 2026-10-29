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
 *  1) GERA os hashes das senhas de teste ("Senha@1" ... "Senha@12"), para
 *     uso no script de massa de dados (sql/02_dml.sql). Como o banco
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
 *  --------------------------------------------------------------
 *  UM DETALHE IMPORTANTE SOBRE O FORMATO DO HASH
 *  --------------------------------------------------------------
 *  O script 02_dml.sql guarda hashes em BCrypt (formato "$2a$10$..."),
 *  que e o formato que existia na base quando o projeto comecou. Eles
 *  CONTINUAM valendo: e o CodificadorDeSenhaDelegado que reconhece o
 *  formato legado e verifica a senha com o algoritmo correspondente.
 *
 *  Este teste e, portanto, a prova de que a migracao para o Argon2id
 *  NAO quebrou o acesso de ninguem: e a verificacao automatizada de que
 *  os hashes antigos ainda abrem a porta.
 *
 *  Ja o hash NOVO gerado pelo codificador sai em Argon2id, com o
 *  prefixo "{argon2}" - e e o formato que a aplicacao grava hoje.
 *
 *  SOBRE O SALT: como o algoritmo sorteia um "salt" novo a cada
 *  execucao, rodar este teste duas vezes produz hashes diferentes (e
 *  igualmente validos) para a mesma senha. Esse e o comportamento
 *  correto do algoritmo - e e por isso que dois usuarios com a mesma
 *  senha nunca ficam com o mesmo hash no banco.
 * ============================================================================
 */
class GeradorHashSenhaTest {

    /** Caminho do script de massa de dados, relativo a pasta do projeto. */
    private static final Path SCRIPT_DML = Paths.get(
            "src", "main", "resources", "sql", "02_dml.sql");

    @Test
    @DisplayName("Gera os hashes das senhas de teste (Senha@1 ... Senha@12)")
    void gerarHashes() {
        PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

        System.out.println();
        System.out.println("=== HASHES PARA A MASSA DE DADOS (formato atual: Argon2id) ===");

        for (int i = 1; i <= 12; i++) {
            System.out.println("Senha@" + i + " -> " + encoder.encode("Senha@" + i));
        }

        System.out.println("=== FIM ===");
        System.out.println();
    }

    /**
     * Le os hashes gravados no script de massa de dados e confere se
     * cada um corresponde a senha "Senha@&lt;posicao&gt;".
     *
     * Os hashes do script estao no formato BCrypt legado ("$2a$10$..."),
     * e e o codificador delegado que reconhece esse formato. Por isso este
     * teste tambem e a prova automatizada de que a migracao para o
     * Argon2id NAO trancou ninguem fora do sistema.
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
