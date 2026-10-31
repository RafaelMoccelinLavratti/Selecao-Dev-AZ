package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.TokenRevogado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repositorio da lista de bloqueio dos tokens de acesso.
 *
 * A chave e a familia da sessao. A busca acontece em toda requisicao que
 * traz um token, por isso o indice e a chave primaria.
 */
@Repository
public interface TokenRevogadoRepository extends JpaRepository<TokenRevogado, String> {

    /**
     * Remove os registros cujo prazo ja passou.
     *
     * O prazo e' o momento em que o ultimo token da familia expirou.
     * Depois disso, o registro nao bloqueia mais nada, e so ocupa espaco.
     *
     * @param limite momento de corte
     * @return quantos registros foram removidos
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TokenRevogado t WHERE t.expiraEm < :limite")
    int removerExpirados(@Param("limite") LocalDateTime limite);
}
