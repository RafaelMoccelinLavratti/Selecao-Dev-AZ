package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO: LEILAO RESUMO  (resposta da tela de leiloes - exercicio 7)
 * ============================================================================
 *
 *  PARA QUE SERVE?
 *  E o formato da resposta de GET /leiloes. Traz o que a tela do
 *  exercicio 7 precisa mostrar:
 *      - razao social do VENDEDOR
 *      - inicio previsto
 *      - TOTAL do leilao (soma de quantidade * valor_inicial dos lotes)
 *
 *  POR QUE UM DTO E NAO A ENTIDADE?
 *  1) Controle de exposicao: a entidade tem 6 campos, o DTO tem 6 campos
 *     escolhidos a mao. Se amanha a entidade ganhar uma coluna sensivel
 *     (nota fiscal, custo, senha), ela NAO vaza automaticamente para a API.
 *  2) Controle de formato: a tela precisa de "total" calculado, que nao
 *     existe na tabela.
 *  3) Desacoplamento: se o banco mudar, so a camada de negocio e os DTOs
 *     mudam. O frontend nao quebra.
 *
 *  O CONSTRUTOR e o que o JPQL usa ("SELECT new ...") - por isso a ordem
 *  dos parametros tem que bater exatamente com a ordem da consulta.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeilaoResumoDTO {

    /** Id do leilao. */
    private Integer id;

    /** Codigo do leilao. */
    private Integer codigo;

    /** Descricao do leilao. */
    private String descricao;

    /** Data e hora previstas para o inicio. */
    private LocalDateTime inicioPrevisto;

    /** Razao social da empresa vendedora (vem da relacao com tb_empresa). */
    private String razaoSocial;

    /** Total do leilao = soma de (quantidade * valorInicial) dos lotes. */
    private BigDecimal total;
}
