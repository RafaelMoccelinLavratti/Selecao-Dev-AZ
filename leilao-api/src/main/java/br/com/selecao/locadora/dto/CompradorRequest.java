package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * ============================================================================
 *  DTO DE ENTRADA: COMPRADOR  (POST /compradores)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE ESTE DTO TEM DOIS IDs E NAO UM?
 *  ------------------------------------------------------
 *  A tabela "tb_comprador" tem chave primaria COMPOSTA: e o par
 *  (empresa, leilao). Nao existe um "id" unico para o vinculo - o
 *  vinculo entre a empresa 3 e o leilao 1 SE IDENTIFICA por esses dois
 *  numeros.
 *
 *  Isso tem uma consequencia pratica em toda a API: qualquer operacao
 *  sobre comprador recebe DOIS ids, e nao um:
 *
 *      GET    /compradores/{empresaId}/{leilaoId}
 *      PUT    /compradores/{empresaId}/{leilaoId}
 *      DELETE /compradores/{empresaId}/{leilaoId}
 *
 *  ------------------------------------------------------
 *  O QUE O DTO GARANTE (E O QUE NAO GARANTE)
 *  ------------------------------------------------------
 *  O "@NotNull" garante que os dois ids foram INFORMADOS.
 *
 *  Ele NAO garante que a empresa existe, nem que o leilao existe, nem
 *  que o vinculo ainda nao foi criado. Essas sao REGRAS DE NEGOCIO, e
 *  moram na camada "business" (a classe CompradorBO), que verifica
 *  cada uma delas e lanca uma excecao com mensagem clara.
 *
 *  A divisao e sempre a mesma no projeto:
 *      - DTO    : forma e preenchimento (validacao de formato);
 *      - BO     : regras do negocio (existencia, duplicidade);
 *      - banco  : garantias que so ele pode dar (chave primaria,
 *                 foreign key, unico).
 *
 *  ------------------------------------------------------
 *  POR QUE UM DTO SEPARADO PARA UM CASO TAO SIMPLES?
 *  ------------------------------------------------------
 *  Porque o DTO de ENTRADA e o de SAIDA sao coisas diferentes, mesmo
 *  aqui. O de entrada tem os DOIS ids e nada mais. O de saida
 *  (CompradorResponse) acrescenta a razao social da empresa e o codigo
 *  do leilao - dados que a tela precisa exibir e que exigiriam
 *  consultas extras se nao fossem incluidos.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompradorRequest {

    /**
     * Id da empresa compradora.
     *
     * @NotNull - e obrigatorio.
     */
    @NotNull(message = "A empresa e obrigatoria.")
    private Integer empresa;

    /**
     * Id do leilao comprado.
     *
     * @NotNull - e obrigatorio.
     */
    @NotNull(message = "O leilao e obrigatorio.")
    private Integer leilao;
}
