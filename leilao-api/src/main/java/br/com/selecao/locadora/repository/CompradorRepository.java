package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Comprador;
import br.com.selecao.locadora.entity.CompradorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio do vinculo comprador.
 *
 * O identificador aqui e composto (empresa, leilao), e por isso o
 * JpaRepository recebe o tipo CompradorId.
 */
@Repository
public interface CompradorRepository extends JpaRepository<Comprador, CompradorId> {

    /** Lista as participacoes de uma empresa como compradora. */
    List<Comprador> findByIdEmpresa(Integer empresaId);

    /** Lista as participacoes de um leilao. */
    List<Comprador> findByIdLeilao(Integer leilaoId);

    /** A empresa ja comprou aquele leilao? Usado para bloquear duplicidade. */
    boolean existsByIdEmpresaAndIdLeilao(Integer empresaId, Integer leilaoId);
}
