package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.dto.LeilaoResumoDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 *  REPOSITORY: LEILAO
 * ============================================================================
 *
 *  Alem do CRUD padrao herdado do JpaRepository, aqui mora a query do
 *  EXERCICIO 7: a listagem de leiloes ja com o NOME do vendedor e o TOTAL
 *  do leilao.
 *
 *  O QUE E O "TOTAL DO LEILAO"?
 *  total = soma de (quantidade * valor_inicial) de todos os lotes do leilao.
 *
 *  POR QUE A CONSULTA FOI ESCrita ASSIM?
 *  - "SELECT new ...": o JPQL (a linguagem de consulta do Hibernate) permite
 *    construir o objeto de resposta direto, sem passar por uma entidade e
 *    sem que o codigo Java tenha que remontar o objeto depois.
 *  - A subconsulta "(SELECT ... FROM Lote lo WHERE lo.leilao = l.id)" e uma
 *    "subconsulta correlacionada": ela roda uma vez para cada leilao da
 *    listagem e soma apenas os lotes daquele leilao. E o padrao correto
 *    para calcular total por agrupamento sem perder a ordem da listagem.
 *  - "COALESCE(..., 0)": se o leilao ainda nao tem nenhum lote, a soma seria
 *    NULL (vazio). O COALESCE troca NULL por 0, garantindo que a tela
 *    nunca mostre "null" como total.
 *
 *  SEGURANCA: como TODO o SQL do projeto e gerado pelo Hibernate com
 *  parametros vinculados, nao existe concatenacao de texto do usuario
 *  dentro de uma consulta (SQL injection).
 * ============================================================================
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
