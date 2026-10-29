package br.com.selecao.locadora;

import br.com.selecao.locadora.config.PasswordEncoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================================
 *  TESTE: CODIFICADOR DE SENHA (BCrypt)
 * ============================================================================
 *
 *  Estes testes NAO precisam de banco de dados - sao rapidos e rodam
 *  em qualquer maquina, o que e essencial para o avaliador poder
 *  executar "mvn test" sem ter o PostgreSQL no ar.
 *
 *  O que eles provam:
 *  1) A senha gravada NAO e a senha digitada (nem comparavel a ela).
 *  2) A senha digitada consegue ser conferida depois.
 *  3) Duas senhas IGUAIS geram hashes DIFERENTES (prova do "salt").
 *  4) O hash tem o formato esperado do BCrypt ($2a$...).
 * ============================================================================
 */
class PasswordEncoderTest {

    /** O mesmo codificador usado pela aplicacao. */
    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    @DisplayName("A senha gravada e um hash, e nao o texto digitado")
    void hashNaoEhTextoPuro() {
        String senha = "SenhaSegura123";
        String hash = encoder.encode(senha);

        assertNotEquals(senha, hash, "o hash NUNCA pode ser igual a senha");
        assertTrue(hash.startsWith("$2a$"), "o hash deve ter o formato do BCrypt");
    }

    @Test
    @DisplayName("A senha correta e reconhecida apos a codificacao")
    void senhaCorretaEhReconhecida() {
        String senha = "SenhaSegura123";
        String hash = encoder.encode(senha);

        assertTrue(encoder.matches(senha, hash), "a senha correta deve ser aceita");
    }

    @Test
    @DisplayName("A senha errada e rejeitada")
    void senhaErradaEhRejeitada() {
        String hash = encoder.encode("SenhaSegura123");

        assertTrue(!encoder.matches("SenhaErrada456", hash), "a senha errada nao pode ser aceita");
    }

    @Test
    @DisplayName("Duas senhas iguais produzem hashes diferentes (existencia do salt)")
    void saltTornaOsHashesDiferentes() {
        String senha = "MesmaSenha";

        String hash1 = encoder.encode(senha);
        String hash2 = encoder.encode(senha);

        assertNotEquals(hash1, hash2,
                "com salt, a mesma senha deve gerar hashes diferentes a cada gravacao");
        assertTrue(encoder.matches(senha, hash1));
        assertTrue(encoder.matches(senha, hash2));
    }
}
