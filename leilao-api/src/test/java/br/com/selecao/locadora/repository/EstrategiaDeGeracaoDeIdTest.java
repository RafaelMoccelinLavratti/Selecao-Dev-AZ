package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Auditoria;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.entity.Lote;
import br.com.selecao.locadora.entity.RefreshToken;
import br.com.selecao.locadora.entity.Unidade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere que a estrategia de geracao do id de cada entidade bate com o
 * que o script de DDL cria de fato.
 *
 * Este teste existe por causa de um bug real.
 *
 * O que aconteceu: a tabela "tb_refresh_token" foi criada com "id
 * BIGSERIAL". O BIGSERIAL do PostgreSQL cria a propria sequencia, com o
 * nome derivado da tabela ("tb_refresh_token_id_seq"), e liga o DEFAULT
 * nela. Mas a entidade pedia "@GeneratedValue(strategy = SEQUENCE,
 * generator = "seq_refresh_token")" - uma sequencia com outro nome, que
 * o script nunca criou.
 *
 * O resultado: o primeiro login quebrava com HTTP 500 ("relation
 * seq_refresh_token does not exist"). E o mais chasto: nenhum teste
 * pegou, porque os testes nao usam banco - a autenticacao so quebrava
 * em producao, na hora em que alguem tentava entrar.
 *
 * A regra que este teste trava:
 *
 *   - coluna com DEFAULT nextval('X') no DDL  => a entidade precisa
 *     gerar o id com "AUTO" e o "@SequenceGenerator" apontando para "X"
 *     (ou o Hibernate usa o DEFAULT, se a estrategia for "IDENTITY");
 *
 *   - coluna BIGSERIAL no DDL  => a entidade precisa gerar o id com
 *     "IDENTITY", porque a sequacao que existe no banco tem o nome
 *     derivado da tabela, e nao o nome que o Hibernate inventaria.
 *
 * Em qualquer dos casos, o que o Hibernate pede tem que existir no
 * banco. E o que o Hibernate pede e' o que o "@SequenceGenerator" (ou a
 * coluna) diz.
 */
class EstrategiaDeGeracaoDeIdTest {

    /**
     * O script de DDL do projeto.
     *
     * E' lido do disco - e nao copiado para dentro do teste - porque uma
     * copia desatualizada nao protegeria nada. Se o DDL mudar, o teste
     * acompanha.
     */
    private static final Path DDL = Paths.get("src", "main", "resources", "sql", "01_ddl.sql");

    /**
     * As entidades que tem id gerado pelo banco.
     */
    private static final Class<?>[] ENTIDADES = {
            Empresa.class,
            Leilao.class,
            Lote.class,
            Unidade.class,
            RefreshToken.class,
            Auditoria.class
    };

    /**
     * Teste 1: toda entidade com "SEQUENCE" tem "@SequenceGenerator", e o
     * nome da sequencia e' o mesmo nos dois lugares.
     *
     * Sem o "@SequenceGenerator", o Hibernate assume um nome padrao
     * ("hibernate_sequence") - que o DDL nao cria. A falha so
     * apareceria em tempo de execucao.
     */
    @Test
    @DisplayName("Toda entidade com geracao por SEQUENCE declara a sequencia que usa")
    void todaSequenciaDeclaradaExisteNaEntidade() {

        List<String> problemas = new ArrayList<>();

        for (Class<?> entidade : ENTIDADES) {

            GeneratedValue geracao = buscarGeracao(entidade);

            if (geracao == null
                    || !GenerationType.SEQUENCE.equals(geracao.strategy())
                    || geracao.generator().isEmpty()) {
                continue;
            }

            String nomeDeclarado = geracao.generator();

            SequenceGenerator anotacao = buscarSequenceGenerator(entidade);

            if (anotacao == null) {
                problemas.add(entidade.getSimpleName()
                        + " usa SEQUENCE com o gerador \"" + nomeDeclarado
                        + "\", mas nao declara o @SequenceGenerator que diz qual e' a sequencia.");
                continue;
            }

            if (!nomeDeclarado.equals(anotacao.sequenceName())) {
                problemas.add(entidade.getSimpleName()
                        + " tem dois nomes para a mesma sequencia: o gerador diz \""
                        + nomeDeclarado + "\" e o @SequenceGenerator diz \""
                        + anotacao.sequenceName() + "\".");
            }
        }

        assertTrue(problemas.isEmpty(),
                "Geracao de id inconsistente na entidade: " + String.join(" | ", problemas));
    }

    /**
     * Teste 2: toda sequencia pedida pela entidade existe no DDL.
     *
     * E' o teste que pegaria o bug do "seq_refresh_token".
     */
    @Test
    @DisplayName("Toda sequencia pedida pela entidade e' criada pelo script de DDL")
    void todaSequenciaPedidaExisteNoDdl() {

        String ddl = lerDdl();

        List<String> problemas = new ArrayList<>();

        for (Class<?> entidade : ENTIDADES) {

            GeneratedValue geracao = buscarGeracao(entidade);

            if (geracao == null
                    || !GenerationType.SEQUENCE.equals(geracao.strategy())
                    || geracao.generator().isEmpty()) {
                continue;
            }

            String nome = geracao.generator();

            // Procura a criacao da sequencia no script.
            Matcher matcher = Pattern
                    .compile("CREATE\\s+SEQUENCE\\s+" + Pattern.quote(nome) + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(ddl);

            if (!matcher.find()) {
                problemas.add("A entidade " + entidade.getSimpleName()
                        + " pede a sequencia \"" + nome
                        + "\", mas o script de DDL nao a cria. "
                        + "O erro apareceria em producao, no primeiro registro gravado.");
            }
        }

        assertTrue(problemas.isEmpty(),
                "Sequencia usada e' inexistente no banco: " + String.join(" | ", problemas));
    }

    /**
     * Teste 3: as tabelas com coluna BIGSERIAL nao podem pedir uma
     * sequencia com nome proprio.
     *
     * O BIGSERIAL cria a sequencia com o nome derivado da tabela
     * ("<tabela>_id_seq"). Se a entidade pedir "SEQUENCE" com outro
     * nome, o Hibernate procura a sequencia errada - e o erro e' um 500
     * em tempo de execucao.
     *
     * E' a regra que impede o bug do "seq_refresh_token" de voltar.
     */
    @Test
    @DisplayName("Tabela com BIGSERIAL usa IDENTITY na entidade, e nao SEQUENCE")
    void tabelaComBigserialUsaIdentity() {

        String ddl = lerDdl();

        List<String> problemas = new ArrayList<>();

        for (Class<?> entidade : ENTIDADES) {

            String tabela = buscarTabela(entidade);

            if (tabela == null) {
                continue;
            }

            if (!tabelaTemColunaBigserial(ddl, tabela)) {
                continue;
            }

            GeneratedValue geracao = buscarGeracao(entidade);

            if (geracao != null && GenerationType.SEQUENCE.equals(geracao.strategy())) {
                problemas.add("A tabela " + tabela + " tem a coluna id como BIGSERIAL, "
                        + "mas a entidade " + entidade.getSimpleName() + " pede SEQUENCE. "
                        + "A sequencia que existe no banco se chama \"" + tabela
                        + "_id_seq\", e nao o nome que o Hibernate pediu.");
            }
        }

        assertTrue(problemas.isEmpty(),
                "Estrategia de geracao incompativel com o DDL: " + String.join(" | ", problemas));
    }

    /**
     * Teste 4: toda entidade com "@GeneratedValue" tem o campo "id"
     * anotado com "@Id".
     *
     * Uma entidade sem "@Id" nao tem chave primaria - e o erro apareceria
     * na hora de gravar, em producao.
     */
    @Test
    @DisplayName("Toda entidade com geracao automatica tem a chave primaria anotada")
    void todaEntidadeComGeracaoTemChavePrimaria() {

        for (Class<?> entidade : ENTIDADES) {

            assertTrue(buscarId(entidade) != null,
                    "A entidade " + entidade.getSimpleName() + " nao tem o campo \"id\" anotado com @Id.");

            assertTrue(buscarGeracao(entidade) != null,
                    "A entidade " + entidade.getSimpleName() + " nao declara @GeneratedValue no campo \"id\".");
        }
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    /**
     * Le o script de DDL do disco.
     */
    private String lerDdl() {

        try {
            return new String(Files.readAllBytes(DDL), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel ler o script de DDL: " + DDL, e);
        }
    }

    /**
     * A anotacao "@GeneratedValue" do campo "id".
     */
    private GeneratedValue buscarGeracao(Class<?> entidade) {

        try {
            return entidade.getDeclaredField("id").getAnnotation(GeneratedValue.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * A anotacao "@Id" do campo "id".
     */
    private Id buscarId(Class<?> entidade) {

        try {
            return entidade.getDeclaredField("id").getAnnotation(Id.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * A anotacao "@SequenceGenerator" do campo "id".
     */
    private SequenceGenerator buscarSequenceGenerator(Class<?> entidade) {

        try {
            return entidade.getDeclaredField("id").getAnnotation(SequenceGenerator.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * O nome da tabela da entidade.
     */
    private String buscarTabela(Class<?> entidade) {

        Entity anotacao = entidade.getAnnotation(Entity.class);

        if (anotacao == null || anotacao.name().isEmpty()) {
            return null;
        }

        return anotacao.name();
    }

    /**
     * A coluna "id" da tabela e' BIGSERIAL?
     *
     * Procura a declaracao da tabela e olha a coluna "id".
     */
    private boolean tabelaTemColunaBigserial(String ddl, String tabela) {

        Matcher tabelaMatcher = Pattern
                .compile("CREATE\\s+TABLE\\s+" + Pattern.quote(tabela) + "\\s*\\(([^;]*?)\\n\\s*\\);",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(ddl);

        if (!tabelaMatcher.find()) {
            return false;
        }

        String corpo = tabelaMatcher.group(1);

        Matcher colunaMatcher = Pattern
                .compile("^\\s*id\\s+([A-Za-z]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
                .matcher(corpo);

        if (!colunaMatcher.find()) {
            return false;
        }

        return "BIGSERIAL".equalsIgnoreCase(colunaMatcher.group(1));
    }
}
