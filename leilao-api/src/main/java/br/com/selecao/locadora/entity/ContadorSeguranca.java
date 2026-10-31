package br.com.selecao.locadora.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Contador de seguranca guardado no banco.
 *
 * Serve para o caso de varias replicas da API: com o contador no banco,
 * o limite vale para a API inteira, e nao para cada replica
 * separadamente.
 *
 * A chave e um texto livre, porque cada controle usa o seu proprio
 * formato ("tentativa:ip|usuario", "requisicao:ip"). O prefixo evita
 * que um controle interfira no contador do outro.
 */
@Getter
@Setter
@Entity
@Table(name = "tb_contador_seguranca", indexes = {
        @Index(name = "tb_contador_seguranca_expiracao_idx", columnList = "expira_em")
})
public class ContadorSeguranca implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Chave do contador. E a chave primaria: uma chave, um contador.
     */
    @Id
    @Column(name = "chave", nullable = false, length = 200)
    private String chave;

    /**
     * Quantidade de ocorrencias dentro da janela atual.
     */
    @Column(name = "contador", nullable = false)
    private Integer contador;

    /**
     * Instante em que a janela expira.
     *
     * Cada ocorrencia renova este prazo - e assim que o controle
     * deslizante funciona.
     */
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;
}
