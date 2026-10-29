package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Auditoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 *  REPOSITORIO: TRILHA DE AUDITORIA
 * ============================================================================
 *
 *  Camada de ACESSO A DADOS. Nenhuma regra de negocio mora aqui - quem
 *  decide o que e gravado e o AuditoriaBO.
 * ============================================================================
 */
@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /**
     * Ultimo registro da cadeia, para encadear o proximo.
     *
     * A ordenacao e pela SEQUENCIA (e nao pelo id), porque a sequencia e
     * o numero inquebravel da trilha.
     */
    Optional<Auditoria> findTopByOrderBySequenciaDesc();

    /**
     * Registro com a maior sequencia - usado na verificacao da cadeia.
     */
    Optional<Auditoria> findFirstByOrderBySequenciaDesc();

    /**
     * Registros a partir de uma sequencia (para verificar a cadeia a
     * partir de um ponto especifico).
     */
    List<Auditoria> findBySequenciaGreaterThanEqualOrderBySequenciaAsc(Long sequencia);

    /**
     * Ultima sequencia gravada. Serve tanto para calcular a proxima
     * quanto para detectar buracos na sequencia.
     */
    @Query("SELECT COALESCE(MAX(a.sequencia), 0) FROM Auditoria a")
    Long obterMaiorSequencia();

    /**
     * Conta quantos registros existem a partir de uma sequencia.
     */
    long countBySequenciaGreaterThanEqual(Long sequencia);

    /**
     * Ultimos registros, em ordem cronologica decrescente.
     */
    List<Auditoria> findAllByOrderBySequenciaDesc(Pageable pageable);

    /**
     * Registros de um usuario especifico.
     */
    List<Auditoria> findByUsuarioOrderBySequenciaDesc(String usuario, Pageable pageable);

    /**
     * Registros dentro de uma janela de tempo.
     */
    @Query("SELECT a FROM Auditoria a WHERE a.dataEvento BETWEEN :inicio AND :fim "
            + "ORDER BY a.sequencia DESC")
    List<Auditoria> buscarPorPeriodo(@Param("inicio") java.time.LocalDateTime inicio,
                                     @Param("fim") java.time.LocalDateTime fim,
                                     Pageable pageable);
}
