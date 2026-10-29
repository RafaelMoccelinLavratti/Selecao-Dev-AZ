package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * ============================================================================
 *  DTO DE ENTRADA: LOTE  (POST /lotes e PUT /lotes/:id)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE E UM DTO E POR QUE ELE EXISTE
 *  ------------------------------------------------------
 *  DTO significa "Data Transfer Object" (objeto de transferencia de
 *  dados). E a classe que representa o que ENTRA na API.
 *
 *  A alternativa seria usar a ENTIDADE (a classe Lote) diretamente no
 *  controller. Isso parece mais simples, mas e uma MA PRATICA, por
 *  tres motivos:
 *
 *  1) SEGURANCA - expoe campos que nao deviam ser enviados.
 *     A entidade tem o campo "id", "createdAt", "updatedAt". Se o
 *     controller usasse a entidade, o cliente poderia mandar um id no
 *     corpo do POST e o servidor aceitaria. Com o DTO, so entram os
 *     campos que o DTO declara.
 *
 *  2) ACOPLAMENTO - a API passaria a mudar toda vez que a tabela
 *     mudasse. Se um dia o banco ganhar uma coluna interna, o
 *     contrato da API mudaria junto - e o frontend quebraria.
 *
 *  3) VALIDACAO - as anotacoes ficam no DTO de entrada, e a
 *     validacao acontece ANTES de o codigo de negocio rodar.
 *
 *  ------------------------------------------------------
 *  O QUE CADA ANOTACAO FAZ
 *  ------------------------------------------------------
 *  | Anotacao    | O que faz                                     |
 *  |-------------|-----------------------------------------------|
 *  | @NotNull    | o campo e obrigatorio (nao pode ser nulo)     |
 *  | @Min        | o valor numerico tem um minimo                |
 *  | @DecimalMin | o valor decimal tem um minimo                 |
 *  | @Size       | o texto tem um tamanho maximo                 |
 *  | @NotBlank   | o texto e obrigatorio E nao pode ser so espacos|
 *
 *  ------------------------------------------------------
 *  POR QUE O VALOR INICIAL USA @DecimalMin E NAO @Min?
 *  ------------------------------------------------------
 *  Porque o valor e um BigDecimal (dinheiro), e nao um inteiro.
 *  O @Min aceita apenas numeros inteiros - se fosse usado com BigDecimal,
 *  o valor 0.50 seria REJEITADO, porque 0.50 e "menor que 1".
 *  O @DecimalMin aceita o valor minimo como texto ("0.0"), que e
 *  justamente a forma de escrever um decimal no Java.
 *
 *  ------------------------------------------------------
 *  DETALHE IMPORTANTE SOBRE "valorInicial"
 *  ------------------------------------------------------
 *  O campo e BigDecimal, e nao double. Isso e ESSENCIAL para dinheiro
 *  (a explicacao completa esta em LoteBO): o double nao representa
 *  0.1 de forma exata, e o erro se acumula ao somar muitos lotes.
 *
 *  E o "leilao" e o ID do leilao ao qual o lote pertence. O
 *  "@NotNull" garante que ele foi informado - mas NAO garante que o
 *  leilao exista de verdade. Essa segunda verificacao (a regra de
 *  negocio) e feita na camada "business", em LoteBO.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoteRequest {

    /**
     * Numero do lote dentro do leilao.
     *
     * Comeca em 1 (e nao em 0): o zero e o que o banco usaria para
     * "sem numero", e nao faz sentido um lote "de numero zero".
     *
     * @Min(1) impede que o valor seja zero ou negativo.
     */
    @Min(value = 1, message = "O numero do lote deve ser maior que zero.")
    private Integer numeroLote;

    /**
     * Descricao do item leiloado.
     *
     * @Size(max = 80) respeita o tamanho maximo da coluna no banco.
     * O limite aqui e o que impede o envio de um texto gigante que
     * nao entraria na coluna (e o banco recusaria com um erro feio).
     */
    @Size(max = 80, message = "A descricao deve ter no maximo 80 caracteres.")
    private String descricao;

    /**
     * Quantidade de itens no lote.
     *
     * @Min(1) - um lote com zero itens nao faz sentido em um leilao.
     */
    @Min(value = 1, message = "A quantidade deve ser maior que zero.")
    private Integer quantidade;

    /**
     * Valor inicial de cada item (em reais).
     *
     * BigDecimal, nunca double - dinheiro em ponto flutuante acumula
     * erro de arredondamento.
     *
     * @DecimalMin("0.0") garante que o preco nunca seja negativo.
     * Dinheiro negativo nao faz sentido em um leilao.
     */
    @DecimalMin(value = "0.0", message = "O valor inicial nao pode ser negativo.")
    private BigDecimal valorInicial;

    /**
     * Descricao da unidade de medida (ex.: "kg", "unidade", "m3").
     *
     * @Size(max = 128) respeita a coluna do banco.
     */
    @Size(max = 128, message = "A unidade deve ter no maximo 128 caracteres.")
    private String unidade;

    /**
     * Id do leilao ao qual o lote pertence.
     *
     * @NotNull - e obrigatorio (asterisco no diagrama).
     *
     * O "@NotNull" garante que o campo chegou preenchido. Ele NAO
     * garante que o leilao exista no banco - essa e uma REGRA DE
     * NEGOCIO, verificada na camada "business" (LoteBO), que lanca
     * RegraNegocioException com uma mensagem clara para o usuario.
     */
    @NotNull(message = "O leilao e obrigatorio.")
    private Integer leilao;
}
