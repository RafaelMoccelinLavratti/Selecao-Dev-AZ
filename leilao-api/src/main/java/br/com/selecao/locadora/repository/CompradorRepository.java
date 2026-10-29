package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Comprador;
import br.com.selecao.locadora.entity.CompradorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 *  REPOSITORY: COMPRADOR
 * ============================================================================
 *
 *  Diferenca em relacao aos outros: o identificador aqui e COMPOSTO
 *  (empresa, leilao). Por isso o JpaRepository recebe o tipo CompradorId
 *  em vez de Integer.
 *
 *  Exemplo do que isso significa na pratica:
 *      repository.findById(new CompradorId(3, 1))  -> empresa 3 no leilao 1
 * ============================================================================
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
