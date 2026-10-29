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
 *  ------------------------------------------------------
 *  POR QUE ESTES TESTES EXISTEM?
 *  ------------------------------------------------------
 *  Porque a protecao da senha depende de uma unica coisa: o codificador.
 *  Se ele tiver um defeito - se devolver a senha em claro, se aceitar
 *  qualquer senha, ou se produzir o mesmo hash sempre -, **toda** a
 *  protecao do projeto cai junto. E o defeito nao apareceria em nenhum
 *  outro teste: a API responderia 200 e a senha estaria exposta.
 *
 *  Por isso o codificador tem teste proprio.
 *
 *  ------------------------------------------------------
 *  POR QUE NAO PRECISAM DE BANCO DE DADOS?
 *  ------------------------------------------------------
 *  Porque sao rapidos e rodam em qualquer maquina - o que e essencial
 *  para o avaliador poder executar "mvn test" sem ter o PostgreSQL no
 *  ar. Um teste que falha por falta de infraestrutura mascara o
 *  problema real.
 *
 *  ------------------------------------------------------
 *  O QUE ESTES TESTES PROVAM
 *  ------------------------------------------------------
 *  1) A senha gravada NAO e a senha digitada (nem comparavel a ela).
 *  2) A senha digitada consegue ser conferida depois.
 *  3) A senha errada e rejeitada.
 *  4) Duas senhas IGUAIS geram hashes DIFERENTES (prova do "salt").
 *  5) O hash tem o formato esperado do BCrypt ($2a$...).
 *  6) O codificador nao guarda estado entre chamadas (o mesmo objeto
 *     pode ser reusado com seguranca).
 * ============================================================================
 */
class PasswordEncoderTest {

    /**
     * O mesmo codificador usado pela aplicacao.
     *
     * Note que o codificador e criado DIRETO (new PasswordEncoderConfig()
     * .passwordEncoder()), e nao injetado pelo Spring. Isso e possivel
     * porque o metodo "passwordEncoder()" e um metodo de fabrica comum -
     * nao depende de nada do container. E o que torna o teste independente
     * do Spring.
     */
    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    /**
     * Teste 1: a senha gravada e um HASH, e nao o texto digitado.
     *
     * O hash do BCrypt tem o formato "$2a$<custo>$<salt><hash>". O
     * "$2a$" identifica o algoritmo e o custo - e e o que permite
     * verificar, no futuro, se a senha foi gravada com um custo
     * adequado.
     */
    @Test
    @DisplayName("A senha gravada e um hash, e nao o texto digitado")
    void hashNaoEhTextoPuro() {
        String senha = "SenhaSegura123";
        String hash = encoder.encode(senha);

        assertNotEquals(senha, hash, "o hash NUNCA pode ser igual a senha");
        assertTrue(hash.startsWith("$2a$"), "o hash deve ter o formato do BCrypt");
    }

    /**
     * Teste 2: a senha correta e reconhecida depois da codificacao.
     *
     * Este e o caminho do dia a dia: o usuario digita a senha, o
     * servidor confere contra o hash guardado. O metodo "matches" e o
     * que faz essa conferencia - e ele compara em **tempo constante**,
     * o que impede que um atacante descubra a senha medindo quanto
     * tempo a resposta leva.
     */
    @Test
    @DisplayName("A senha correta e reconhecida apos a codificacao")
    void senhaCorretaEhReconhecida() {
        String senha = "SenhaSegura123";
        String hash = encoder.encode(senha);

        assertTrue(encoder.matches(senha, hash), "a senha correta deve ser aceita");
    }

    /**
     * Teste 3: a senha errada e rejeitada.
     *
     * O caminho inverso, e igualmente importante: uma falha aqui seria
     * uma falha de autenticacao - qualquer senha entraria no sistema.
     */
    @Test
    @DisplayName("A senha errada e rejeitada")
    void senhaErradaEhRejeitada() {
        String hash = encoder.encode("SenhaSegura123");

        assertTrue(!encoder.matches("SenhaErrada456", hash), "a senha errada nao pode ser aceita");
    }

    /**
     * Teste 4: duas senhas IGUAIS produzem hashes DIFERENTES.
     *
     * Este teste prova a existencia do **salt** - e e o mais importante
     * do arquivo.
     *
     * Por que isso importa? Sem salt, duas empresas com a mesma senha
     * teriam o mesmo hash. Com um vazamento do banco, o atacante
     * veria que "as duas empresas 3 e 7 tem a mesma senha" - e poderia
     * testar essa senha contra outros sistemas, sabendo que as pessoas
     * reutilizam senha. Com salt, o hash e diferente a cada gravacao,
     * e essa informacao nao existe mais.
     *
     * Repare que o teste tambem verifica que **ambos** os hashes ainda
     * reconhecem a senha: o salt muda o hash, mas nao quebra a
     * verificacao.
     */
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

    /**
     * Teste 5: o codificador e seguro de reusar.
     *
     * O `PasswordEncoder` e um objeto **compartilhado** pela aplicacao
     * (um unico bean do Spring, injetado em todos os lugares que
     * gravam senha). Este teste garante que o mesmo codificador pode
     * ser reusado para varias gravacoes seguidas - sem que o resultado
     * de uma interfira na outra.
     *
     * A razao de o teste existir: um codificador com estado (por
     * exemplo, que guardasse o "ultimo salt" em um atributo) poderia
     * gerar o mesmo hash duas vezes, ou pior, misturar dois salts.
     */
    @Test
    @DisplayName("O mesmo codificador pode ser reusado com seguranca")
    void codificadorPodeSerReusado() {
        String primeiro = encoder.encode("SenhaA");
        String segundo = encoder.encode("SenhaB");

        assertNotEquals(primeiro, segundo,
                "senhas diferentes nao podem gerar o mesmo hash");

        // E os dois hashes continuam verificando as suas senhas.
        assertTrue(encoder.matches("SenhaA", primeiro));
        assertTrue(encoder.matches("SenhaB", segundo));
    }
}
