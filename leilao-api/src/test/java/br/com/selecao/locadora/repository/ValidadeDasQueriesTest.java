package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.dto.LeilaoResumoDTO;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.entity.Lote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * Valida as queries dos repositorios.
 *
 * Uma query JPQL errada nao aparece em nenhum erro de compilacao: o
 * Java compila um "@Query" sem olhar o que esta dentro da string. O
 * erro so apareceria em tempo de execucao, quando alguem chamasse o
 * metodo - ou seja, em producao, no meio de uma requisicao.
 *
 * Por isso este teste existe. Ele cria cada uma das queries delicatezas
 * do projeto, e o Hibernate as analisa contra o metamodelo. Query
 * invalida (sintaxe errada, atributo que nao existe, entidade com nome
 * errado) estoura aqui - no build, e nao em producao.
 *
 * O banco nao e necessario: o Hibernate monta o metamodelo e analisa o
 * JPQL sem abrir conexao. E por isso que este teste roda no CI, que nao
 * tem PostgreSQL.
 *
 * As queries do projeto ficam em constantes publicas (JPQL_RESUMO_VISIVEL,
 * JPQL_VISIVEIS), e o teste usa exatamente essas constantes. Se a
 * constante mudar, o teste acompanha - uma copia desatualizada do JPQL
 * no teste nao protegeria nada.
 */
@DataJpaTest(properties = {
        // URL inexistente: se algo tentar conectar, o teste falha
        // dizendo que o teste exige banco - e nao que a query esta
        // errada.
        "spring.datasource.url=jdbc:postgresql://localhost:1/base_sem_banco",
        "spring.datasource.username=teste",
        "spring.datasource.password=teste",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        // O Hibernate nao cria nem altera tabelas (quem faz isso e' o
        // script de DDL), e o dialeto vem da configuracao - assim ele
        // nao precisa perguntar ao banco qual e' a versao.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Sem transacao: e a transacao que abre a conexao com o banco, e este
// teste nao quer banco - ele so quer que o Hibernate analise as
// queries.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ValidadeDasQueriesTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /**
     * A query dos leiloes visiveis para uma empresa, com o total.
     *
     * E a mais delicada do projeto: tem uma subquery que soma os lotes
     * (o total do leilao) e outra subquery que verifica a participacao
     * como compradora - dentro de uma chave primaria composta.
     */
    @Test
    @DisplayName("A query dos leiloes visiveis (com total) e' valida")
    void aQueryDosLeiloesVisiveisComTotalEValida() {

        EntityManager em = entityManagerFactory.createEntityManager();

        // Se o JPQL tiver erro de sintaxe, ou se algum atributo ou
        // entidade nao existir, a criacao da query estoura aqui.
        em.createQuery(LeilaoRepository.JPQL_RESUMO_VISIVEL, LeilaoResumoDTO.class);

        em.close();
    }

    /**
     * A query dos leiloes visiveis, devolvendo a entidade.
     */
    @Test
    @DisplayName("A query dos leiloes visiveis (entidade) e' valida")
    void aQueryDosLeiloesVisiveisEntidadeEValida() {

        EntityManager em = entityManagerFactory.createEntityManager();

        em.createQuery(LeilaoRepository.JPQL_VISIVEIS, Leilao.class);

        em.close();
    }

    /**
     * A query dos lotes visiveis.
     *
     * A mais delicada de todas: o "c.id.leilao" e' um Integer dentro de
     * uma chave @EmbeddedId, comparado com o id de uma entidade. Se o
     * Hibernate nao souber interpretar essa comparacao, o erro so
     * apareceria em producao.
     */
    @Test
    @DisplayName("A query dos lotes visiveis e' valida")
    void aQueryDosLotesVisiveisEValida() {

        EntityManager em = entityManagerFactory.createEntityManager();

        em.createQuery(LoteRepository.JPQL_VISIVEIS, Lote.class);

        em.close();
    }
}
