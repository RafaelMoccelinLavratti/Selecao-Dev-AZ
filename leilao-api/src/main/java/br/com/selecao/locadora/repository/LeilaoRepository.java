package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.dto.LeilaoResumoDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
