package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * DTO de entrada do vinculo comprador.
 *
 * Recebe os dois ids e nada mais. O @NotNull garante que os ids foram
 * informados; a existencia da empresa e do leilao e checada na camada de
 * negocio.
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
