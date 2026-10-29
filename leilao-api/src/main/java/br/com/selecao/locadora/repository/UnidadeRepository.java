package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ============================================================================
 *  REPOSITORY: UNIDADE
 * ============================================================================
 *
 *  Acesso a tabela de unidades. A unica regra extra e a unicidade do nome,
 *  validada antes de gravar para devolver uma mensagem de erro legivel.
 * ============================================================================
 */
@Repository
public interface UnidadeRepository extends JpaRepository<Unidade, Integer> {

    /** Ja existe uma unidade com este nome? */
    boolean existsByNome(String nome);

    /** Existe uma unidade com este nome em OUTRO id (validacao no UPDATE)? */
    boolean existsByNomeAndIdNot(String nome, Integer id);
}
