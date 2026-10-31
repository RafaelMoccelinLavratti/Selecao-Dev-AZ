package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio do lote.
 *
 * Alem do CRUD, tem a consulta dos lotes de um leilao especifico, usada no
 * calculo do total e no detalhe.
 */
@Repository
public interface LoteRepository extends JpaRepository<Lote, Integer> {

    /** Lista todos os lotes de um leilao especifico. */
    List<Lote> findByLeilaoId(Integer leilaoId);

    /** Indica se o leilao possui ao menos um lote (regra de negocio). */
    boolean existsByLeilaoId(Integer leilaoId);

    /**
     * JPQL dos lotes visiveis para uma empresa.
     *
     * A query fica numa constante (e nao escrita direto na anotacao)
     * por um motivo pratico: o teste de validade das queries precisa
     * criar exatamente esta query, para o Hibernate analysar o JPQL. Se
     * o texto estivesse solto na anotacao, o teste teria uma copia -
     * e uma copia desatualizada nao protege nada.
     */
    String JPQL_VISIVEIS =
            "SELECT lo FROM Lote lo "
                    + "WHERE lo.leilao.vendedor.id = :empresaId "
                    + "   OR EXISTS (SELECT c FROM Comprador c "
                    + "              WHERE c.id.leilao = lo.leilao.id AND c.id.empresa = :empresaId) "
                    + "ORDER BY lo.id";

    /**
     * Lista os lotes que uma empresa pode ver.
     *
     * O lote herda o acesso do leilao: e visivel quando a empresa vende
     * o leilao ou esta inscrita como compradora.
     *
     * @param empresaId id da empresa
     * @return os lotes visiveis
     */
    @Query(JPQL_VISIVEIS)
    List<Lote> findVisiveisParaEmpresa(@Param("empresaId") Integer empresaId);
}
