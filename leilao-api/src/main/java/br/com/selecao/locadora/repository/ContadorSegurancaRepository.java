package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.ContadorSeguranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repositorio dos contadores de seguranca guardados no banco.
 *
 * A tabela existe para o caso de varias replicas da API: com o
 * contador no banco, o limite vale para a API inteira e nao para cada
 * replica separadamente.
 */
@Repository
public interface ContadorSegurancaRepository extends JpaRepository<ContadorSeguranca, String> {

    /**
     * Incrementa o contador de forma atomica.
     *
     * O "ON CONFLICT" e' o que torna a operacao atomica: e' uma unica
     * instrucao no banco, entao duas requisicoes que chegam ao mesmo
     * tempo nao se perdem. Fez-se com "SELECT e depois UPDATE", as duas
     * leriam o mesmo valor e uma das incremento seria perdida - que e
     * exatamente o buraco que o atacante procuraria para furar o limite.
     *
     * A janela e' renovada a cada incremento: e assim que o controle
     * deslizante funciona (o atacante tem 5 tentativas a cada 15
     * minutos, e nao 5 no total para sempre).
     *
     * Se a janela anterior ja venceu, a contagem recomeca em 1.
     *
     * @param chave  identificador do contador
     * @param agora  instante de referencia, para testar a validade
     * @param expira novo fim da janela
     * @return quantas linhas foram afetadas (1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO tb_contador_seguranca (chave, contador, expira_em) "
            + "VALUES (:chave, 1, :expira) "
            + "ON CONFLICT (chave) DO UPDATE SET "
            + "    contador = CASE WHEN tb_contador_seguranca.expira_em < :agora "
            + "                    THEN 1 "
            + "                    ELSE tb_contador_seguranca.contador + 1 END, "
            + "    expira_em = :expira",
            nativeQuery = true)
    int incrementar(@Param("chave") String chave,
                    @Param("agora") LocalDateTime agora,
                    @Param("expira") LocalDateTime expira);

    /**
     * Remove os contadores cuja chave comeca com o prefixo.
     *
     * O "position(... in ...) = 1" e' o "comeca com" do PostgreSQL. A
     * alternativa seria o "LIKE", mas ele interpreta "%" e "_" como
     * curinga - e um nome de usuario com esses caracteres poderia
     * apagar o contador de outro usuario. Aqui a comparacao e' de
     * texto puro.
     *
     * O "starts_with" do PostgreSQL 11+ faria o mesmo, mas o projeto
     * declara suportar PostgreSQL 10, e essa funcao nao existe la.
     *
     * @param prefixo inicio da chave
     * @return quantos registros foram removidos
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM tb_contador_seguranca c "
            + "WHERE position(:prefixo in c.chave) = 1",
            nativeQuery = true)
    int removerPorPrefixo(@Param("prefixo") String prefixo);

    /**
     * Remove os contadores cuja janela ja venceu.
     *
     * @param limite momento de corte
     * @return quantos registros foram removidos
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ContadorSeguranca c WHERE c.expiraEm < :limite")
    int removerExpirados(@Param("limite") LocalDateTime limite);
}
