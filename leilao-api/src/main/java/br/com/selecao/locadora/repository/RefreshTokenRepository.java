package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 *  REPOSITORIO: TOKEN DE RENOVACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE UM REPOSITORIO FAZ?
 *  ------------------------------------------------------
 *  E a camada de ACESSO AOS DADOS. O enunciado define exatamente este
 *  pacote ("Camada de acesso a dados (Repository)"), e a regra e simples:
 *  aqui mora a conversa com o banco, e NADA mais.
 *
 *  Repositorio nao tem regra de negocio e nao conhece HTTP. Ele sabe
 *  guardar, buscar e apagar - e mais nada. Quem decide QUANDO buscar e
 *  O QUE fazer com o resultado e a camada de negocio (business).
 *
 *  ------------------------------------------------------
 *  A BUSCA PELO HASH
 *  ------------------------------------------------------
 *  O metodo "buscarPorHash" usa o SHA-256 do token como criterio de
 *  busca. Isso e seguro porque:
 *    - o indice sobre a coluna garante performance (sem varredura);
 *    - o valor procurado e um hash, nao um token - quem estiver
 *      olhando o log de consulta ve um valor sem utilidade.
 * ============================================================================
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

    /**
     * Busca o registro pelo hash do token.
     *
     * @param hash SHA-256 do token de renovacao
     * @return o registro, ou vazio se o token nao existir
     */
    Optional<RefreshToken> findByTokenHash(String hash);

    /**
     * Lista todos os tokens de uma mesma familia.
     *
     * @param familia identificador da familia de tokens
     * @return os tokens da familia
     */
    List<RefreshToken> findByFamilia(String familia);

    /**
     * Lista todos os tokens de uma familia que ainda NAO foram revogados.
     *
     * @param familia identificador da familia de tokens
     * @return os tokens validos da familia
     */
    List<RefreshToken> findByFamiliaAndDataRevogacaoIsNull(String familia);

    /**
     * Revoga (marca com data) todos os tokens validos de uma familia.
     *
     * E a operacao de resposta ao DETAECCAO DE REUSO: quando um token ja
     * usado aparece de novo, toda a familia e invalidada de uma vez.
     *
     * @param familia identificador da familia de tokens
     * @param momento instante da revogacao
     * @return quantos tokens foram revogados
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.dataRevogacao = :momento "
            + "WHERE t.familia = :familia AND t.dataRevogacao IS NULL")
    int revogarFamilia(@Param("familia") String familia, @Param("momento") LocalDateTime momento);

    /**
     * Remove os tokens expirados ha mais de um dia.
     *
     * A margem de um dia existe para preservar a trilha de auditoria de
     * um token que expirou enquanto o servidor estava fora do ar - o
     * registro ainda e interesse para investigar.
     *
     * @param limite momento de corte
     * @return quantos tokens foram removidos
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.dataExpiracao < :limite")
    int removerExpirados(@Param("limite") LocalDateTime limite);
}
