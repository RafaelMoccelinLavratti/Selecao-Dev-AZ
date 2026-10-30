package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 *  DTO DE SAIDA: UNIDADE  (o que a API devolve)
 *
 *  Repare que o campo "createdAt"/"updatedAt" aparece aqui. Isso e
 *  proposital: a trilha de auditoria faz parte do que o usuario pode
 *  consultar. E repare que NAO existe nenhum campo sensivel aqui.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeResponse {

    /** Id do registro. */
    private Integer id;

    /** Nome da unidade. */
    private String nome;

    /** Momento da criacao do registro. */
    private LocalDateTime createdAt;

    /** Momento da ultima alteracao. */
    private LocalDateTime updatedAt;
}
