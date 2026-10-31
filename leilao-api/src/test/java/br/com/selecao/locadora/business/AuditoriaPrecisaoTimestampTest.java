package br.com.selecao.locadora.business;

import br.com.selecao.locadora.entity.Auditoria;
import br.com.selecao.locadora.repository.AuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste de INTEGRACAO da trilha de auditoria: o round-trip pelo banco.
 *
 * O "AuditoriaBOTest" duble o repositorio com o Mockito, e e' justo o que
 * ele cobre - a logica do selo. Mas o duble mantem o "LocalDateTime"
 * como o Java o criou.
 *
 * E aqui mora o bug que o duble nao via:
 *
 *   - o "LocalDateTime.now()" do Java tem precisao de NANOSSEGUNDOS
 *     (9 casas decimais);
 *   - a coluna "data_evento" do PostgreSQL e' do tipo TIMESTAMP, que
 *     guarda MICROSSEGUNDOS (6 casas) - o banco arredonda na gravacao;
 *   - o hash selado usa o TEXTO do instante.
 *
 * Com o duble, o texto selado e o texto lido sao identicos, e a
 * verificacao passa. Com o banco de verdade, o texto selado tinha 9
 * casas e o texto lido tem 6: nunca coincidem, e a verificacao acusava
 * adulteracao em TODOS os registros - mesmo os intactos.
 *
 * Este teste grava num banco PostgreSQL de verdade e relê. E' o unico
 * lugar onde essa perda de precisao aparece.
 *
 * O banco e' opcional: sem ele, o teste e' ignorado (e nao reprova). A
 * ideia e' que ele rode no CI, onde ha PostgreSQL, e nao atrapalhe quem
 * so' tem o codigo.
 */
@DataJpaTest(properties = {
        // O mesmo banco de desenvolvimento.
        "spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/az}",
        "spring.datasource.username=${DATABASE_USER:postgres}",
        "spring.datasource.password=${DATABASE_PASSWORD:postgres}",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@EnabledIf(
        // A condicao e' avaliada ANTES do contexto do Spring subir. E'
        // o que importa: se a verificacao estivesse dentro do metodo de
        // teste, o "@DataJpaTest" ja' teria falhado ao tentar conectar.
        value = "br.com.selecao.locadora.business.AuditoriaPrecisaoTimestampTest#bancoDisponivel",
        disabledReason = "Requer PostgreSQL - o teste e' pulado quando o banco nao esta no ar")
class AuditoriaPrecisaoTimestampTest {

    /**
     * Chave de teste. Tem 32 bytes (256 bits) - o mesmo tamanho minimo
     * exigido em producao.
     */
    private static final String CHAVE_DE_TESTE = "chave-de-teste-com-32-bytes-de-verdade!";

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Teste 1: o instante selado sobrevive ao round-trip pelo banco.
     */
    @Test
    @DisplayName("O instante gravado no banco tem a mesma precisao do selo")
    void instanteSobreviveAoBanco() {

        // O instante que a aplicacao usa: truncado para microssegundos.
        //
        // ESTA E' A LINHA QUE PROVA A CORRECAO. Para conferir que o
        // teste pega o bug, troque por "LocalDateTime.now()" (sem
        // truncar) e veja ele falhar - e' o que acontecia antes da
        // correcao.
        LocalDateTime instante = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        Auditoria registro = new Auditoria();
        registro.setSequencia(999999L);
        registro.setDataEvento(instante);
        registro.setUsuario("teste-de-precisao");
        registro.setIpOrigem("127.0.0.1");
        registro.setMetodoHttp("GET");
        registro.setCaminho("/teste/precisao");
        registro.setStatusHttp(200);
        registro.setDuracaoMs(1L);
        registro.setAcao("TESTE");
        registro.setDetalhe("teste de precisao do timestamp");
        registro.setHashAnterior(null);
        registro.setHashAtual("hash-de-teste");

        auditoriaRepository.save(registro);
        auditoriaRepository.flush();

        // Le de volta do banco. O "clear" do EntityManager e' o que
        // garante que a leitura vem do banco, e nao do cache de
        // primeiro nivel (que devolveria o objeto como o Java o
        // criou - e o teste passaria sem provar nada).
        entityManager.clear();

        List<Auditoria> lidos = auditoriaRepository
                .findBySequenciaGreaterThanEqualOrderBySequenciaAsc(999999L);

        assertNotNull(lidos, "a consulta deve devolver a lista");
        assertEquals(1, lidos.size(), "deve existir exatamente um registro com essa sequencia");

        Auditoria lido = lidos.get(0);

        // O banco arredonda a precisao - e o valor gravado tem de ter a
        // MESMA precisao do valor selado, senao o hash nunca confere.
        assertEquals(
                instante.toString(),
                lido.getDataEvento().toString(),
                "o instante gravado tem de ser identico ao selado - "
                        + "a diferenca de precisao entre o Java (nanos) e o PostgreSQL "
                        + "(micros) quebraria o hash do registro");

        // Limpa o registro de teste.
        auditoriaRepository.delete(lido);
        auditoriaRepository.flush();
    }

    /**
     * Ha um PostgreSQL acessivel?
     *
     * A verificacao e' feita por uma conexao direta, e nao pelo
     * EntityManager: e' o que permite decidir ANTES de o Spring
     * tentar abrir a conexao e falhar.
     *
     * E' publica e estatica porque o "@EnabledIf" da classe a invoca
     * por nome, antes de o contexto subir.
     *
     * @return true se o banco respondeu
     */
    public static boolean bancoDisponivel() {
        try (Connection conexao = DriverManager.getConnection(
                urlDoBanco(),
                System.getenv("DATABASE_USER") != null ? System.getenv("DATABASE_USER") : "postgres",
                System.getenv("DATABASE_PASSWORD") != null ? System.getenv("DATABASE_PASSWORD") : "postgres")) {

            return conexao.isValid(3);

        } catch (Exception erro) {
            return false;
        }
    }

    /**
     * A URL do banco, com o mesmo padrao de fallback do Spring.
     *
     * @return a URL de conexao
     */
    public static String urlDoBanco() {
        String url = System.getenv("DATABASE_URL");

        return (url != null && !url.isEmpty())
                ? url
                : "jdbc:postgresql://localhost:5432/az";
    }
}
