package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio da unidade.
 *
 * Alem do CRUD, tem a consulta que verifica nome repetido - validada antes
 * de gravar para devolver uma mensagem legivel.
 */
@Repository
public interface UnidadeRepository extends JpaRepository<Unidade, Integer> {

    /** Ja existe uma unidade com este nome? */
    boolean existsByNome(String nome);

    /** Existe uma unidade com este nome em outro id (validacao no UPDATE)? */
    boolean existsByNomeAndIdNot(String nome, Integer id);
}
