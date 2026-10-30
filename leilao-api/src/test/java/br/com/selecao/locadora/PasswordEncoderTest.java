package br.com.selecao.locadora;

import br.com.selecao.locadora.config.PasswordEncoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do codificador de senha.
 *
 * A protecao da senha depende de uma unica coisa: o codificador. Se ele
 * devolver a senha em claro, ou aceitar a senha errada, todo o resto cai
 * junto.
 *
 * Nao precisam de banco: sao rapidos e rodam em qualquer maquina, o que
 * permite executar "mvn test" sem o PostgreSQL no ar.
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
     * O hash do Argon2id tem o formato
     * "{argon2}$argon2id$v=19$m=...,t=...,p=...$<sal>$<hash>". O
     * prefixo "{argon2}" identifica o algoritmo - e e o que permite
     * trocar de algoritmo no futuro sem perder as senhas.
     */
    @Test
    @DisplayName("A senha gravada e um hash, e nao o texto digitado")
    void hashNaoEhTextoPuro() {
        String senha = "SenhaSegura123";
        String hash = encoder.encode(senha);

        assertNotEquals(senha, hash, "o hash NUNCA pode ser igual a senha");
        assertTrue(hash.startsWith("{argon2}$argon2id$"),
                "o hash deve ter o prefixo {argon2} e o formato do Argon2id");
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

    /**
     * Teste 6: o hash legado em BCrypt continua funcionando.
     *
     * Este e o teste que garante a TRANSIACAO sem queda de acesso.
     *
     * A base de dados deste projeto foi criada com BCrypt. Se a troca
     * para o Argon2id fosse feita de uma vez, todas as empresas ja
     * cadastradas ficariam sem conseguir entrar. Por isso o codificador
     * reconhece o formato antigo (sem prefixo) e ainda assim confere a
     * senha - e o `upgradeEncoding` devolve "true", sinalizando que
     * aquele hash deve ser reescrito no formato novo.
     *
     * O hash abaixo e o BCrypt (custo 10) da senha "SenhaLegada123",
     * gerado e conferido por um codificador BCrypt puro.
     */
    @Test
    @DisplayName("O hash legado em BCrypt continua sendo conferido")
    void hashLegadoEmBcryptContinuaFuncionando() {

        String hashLegado = new BCryptPasswordEncoder(10).encode("SenhaLegada123");

        assertTrue(hashLegado.startsWith("$2a$"), "premissa do teste: hash BCrypt puro");
        assertTrue(encoder.matches("SenhaLegada123", hashLegado),
                "a senha do hash legado deve ser reconhecida");
        assertTrue(!encoder.matches("SenhaErrada", hashLegado),
                "a senha errada nao pode ser aceita contra o hash legado");
    }

    /**
     * Teste 7: o hash legado e sinalizado para reescrita.
     *
     * E o que faz a migracao acontecer sozinha, no ritmo do uso: quem
     * loga hoje com uma senha em BCrypt passa a ter o hash reescrito em
     * Argon2id, sem script e sem janela de manutencao.
     */
    @Test
    @DisplayName("O hash legado e sinalizado para migracao automatica")
    void hashLegadoSinalizadoParaReescrita() {

        String hashLegado = new BCryptPasswordEncoder(10).encode("SenhaLegada123");
        String hashNovo = encoder.encode("SenhaLegada123");

        assertTrue(encoder.upgradeEncoding(hashLegado),
                "o hash em BCrypt deve ser marcado para reescrita");
        assertTrue(!encoder.upgradeEncoding(hashNovo),
                "o hash ja em Argon2id nao deve ser reescrito");
    }
}
