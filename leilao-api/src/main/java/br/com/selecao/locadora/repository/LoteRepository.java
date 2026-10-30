package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
