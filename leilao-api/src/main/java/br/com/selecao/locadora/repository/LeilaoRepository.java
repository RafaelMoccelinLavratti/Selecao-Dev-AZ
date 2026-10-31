package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.dto.LeilaoResumoDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio do leilao.
 *
 * Alem do CRUD, mora aqui a query do exercicio 7: a listagem com o nome do
 * vendedor e o total do leilao (soma de quantidade x valor inicial dos
 * lotes), calculada no banco.
 */
@Repository
public interface LeilaoRepository extends JpaRepository<Leilao, Integer> {

    /**
     * JPQL dos leiloes visiveis para uma empresa, com o total.
     *
     * A query fica numa constante (e nao escrita direto na anotacao)
     * por um motivo pratico: o teste de validade das queries precisa
     * criar exatamente esta query, para o Hibernate analysar o JPQL. Se
     * o texto estivesse solto na anotacao, o teste teria uma copia -
     * e uma copia desatualizada nao protege nada.
     */
    String JPQL_RESUMO_VISIVEL =
            "SELECT new br.com.selecao.locadora.dto.LeilaoResumoDTO("
                    + "l.id, "
                    + "l.codigo, "
                    + "l.descricao, "
                    + "l.inicioPrevisto, "
                    + "l.vendedor.razaoSocial, "
                    + "(SELECT COALESCE(SUM(lo.quantidade * lo.valorInicial), 0) "
                    + " FROM Lote lo WHERE lo.leilao = l.id)) "
                    + "FROM Leilao l "
                    + "WHERE l.vendedor.id = :empresaId "
                    + "   OR EXISTS (SELECT c FROM Comprador c "
                    + "              WHERE c.id.leilao = l.id AND c.id.empresa = :empresaId) "
                    + "ORDER BY l.id";

    /**
     * JPQL dos leiloes visiveis para uma empresa, com a entidade.
     */
    String JPQL_VISIVEIS =
            "SELECT l FROM Leilao l "
                    + "WHERE l.vendedor.id = :empresaId "
                    + "   OR EXISTS (SELECT c FROM Comprador c "
                    + "              WHERE c.id.leilao = l.id AND c.id.empresa = :empresaId) "
                    + "ORDER BY l.id";

    /**
     * Lista todos os leiloes com: id, codigo, descricao, inicio previsto,
     * razao social do vendedor e o total calculado dos lotes.
     */
    @Query("SELECT new br.com.selecao.locadora.dto.LeilaoResumoDTO("
            + "l.id, "
            + "l.codigo, "
            + "l.descricao, "
            + "l.inicioPrevisto, "
            + "l.vendedor.razaoSocial, "
            + "(SELECT COALESCE(SUM(lo.quantidade * lo.valorInicial), 0) "
            + " FROM Lote lo WHERE lo.leilao = l.id)) "
            + "FROM Leilao l "
            + "ORDER BY l.id")
    List<LeilaoResumoDTO> findResumoLeiloes();

    /**
     * Lista os leiloes que uma empresa pode ver.
     *
     * Sao dois casos: os leiloes que ela vende (a coluna "vendedor") e
     * os leiloes em que ela esta inscrita como compradora (a tabela de
     * vinculo). A empresa nao enxerga o que nao tem relacao com ela.
     *
     * A listagem vem com o total calculado, como a consulta do
     * exercicio 7 - o filtro entra na mesma consulta, em vez de filtrar
     * depois em Java. Filtrar depois traria do banco os leiloes de
     * todo mundo para depois descartar, e ainda poderia errar o total.
     *
     * @param empresaId id da empresa
     * @return os leiloes visiveis para a empresa
     */
    @Query(JPQL_RESUMO_VISIVEL)
    List<LeilaoResumoDTO> findResumoVisivelParaEmpresa(@Param("empresaId") Integer empresaId);

    /**
     * Lista os leiloes visiveis para uma empresa, com a entidade.
     *
     * @param empresaId id da empresa
     * @return os leiloes visiveis
     */
    @Query(JPQL_VISIVEIS)
    List<Leilao> findVisiveisParaEmpresa(@Param("empresaId") Integer empresaId);
}
