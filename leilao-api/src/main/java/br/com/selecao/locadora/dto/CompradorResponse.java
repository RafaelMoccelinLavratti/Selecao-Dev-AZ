package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE SAIDA: COMPRADOR  (o que a API devolve)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE FAZ UM DTO DE SAIDA SER DIFERENTE DO DE ENTRADA?
 *  ------------------------------------------------------
 *  Eles tem papeis diferentes, e isso e proposital:
 *
 *    - O DTO de ENTRADA  define o que o cliente PODE enviar.
 *    - O DTO de SAIDA    define o que o cliente VAI receber.
 *
 *  Nem sempre sao o inverso um do outro. Por exemplo, o CompradorRequest
 *  tem apenas "empresa" e "leilao"; o CompradorResponse tem os mesmos
 *  dois, mas ADICIONA a razao social da empresa e o codigo do leilao.
 *
 *  ------------------------------------------------------
 *  POR QUE INCLUIR A RAZAO SOCIAL E O CODIGO DO LEILAO?
 *  ------------------------------------------------------
 *  Para evitar o problema classico do "N+1": se a tela precisasse
 *  exibir apenas os ids, ela teria que fazer uma consulta extra para
 *  CADA linha da tabela, so para descobrir o nome da empresa.
 *
 *  Como o vinculo comprador quase sempre aparece em LISTAS, resolver
 *  isso no backend (com uma consulta JOIN ou com o preenchimento feito
 *  na propria camada de negocio) economiza dezenas de idas ao servidor.
 *
 *  O preenchimento e feito em CompradorBO, no metodo "paraResponse".
 *
 *  ------------------------------------------------------
 *  A TRILHA DE AUDITORIA
 *  ------------------------------------------------------
 *  Os campos "createdAt" e "updatedAt" fazem parte da resposta de
 *  proposito: eles sao a trilha de auditoria da tabela, e uma tela
 *  interna precisa saber quando o registro foi criado e alterado pela
 *  ultima vez. Nao ha problema em exibi-los - sao informacoes
 *  operacionais, nao dados sensiveis.
 *
 *  Repare no que este DTO NAO tem: nenhum campo de senha, nenhum
 *  dado de contato sensivel. O DTO de saida e a fronteira que garante
 *  que a entidade (com todos os seus campos) nunca vaze inteira para
 *  o cliente.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompradorResponse {

    /**
     * Id da empresa compradora.
     *
     * Faz parte da chave composta do vinculo.
     */
    private Integer empresa;

    /**
     * Razao social da empresa compradora.
     *
     * Preenchido pelo backend para a tela exibir algo legivel, sem
     * precisar de uma consulta extra por linha.
     */
    private String razaoSocial;

    /**
     * Id do leilao comprado.
     *
     * Faz parte da chave composta do vinculo.
     */
    private Integer leilao;

    /**
     * Codigo do leilao comprado.
     *
     * Como a tela mostra "#12" e nao "12", este campo e mais legivel
     * que o id.
     */
    private Integer codigoLeilao;

    /**
     * Momento da criacao do registro.
     */
    private LocalDateTime createdAt;

    /**
     * Momento da ultima alteracao do registro.
     */
    private LocalDateTime updatedAt;
}
