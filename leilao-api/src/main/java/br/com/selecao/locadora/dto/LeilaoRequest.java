package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO DE ENTRADA: LEILAO
 * ============================================================================
 *
 *  "vendedor" e o ID da empresa vendedora (e nao um objeto empresa inteiro).
 *  Isso e proposital: o cliente informa QUAL empresa, e o backend busca
 *  a entidade no banco. Assim:
 *   - o payload fica pequeno e previsivel;
 *   - nao existe a chance de o cliente "inventar" uma empresa no corpo
 *     da requisicao (o que em uma API sem cuidado permitiria gravar
 *     dados inconsistentes);
 *   - a integridade referencial fica garantida pelo banco e pelo codigo.
 * ============================================================================
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeilaoRequest {

    /** Codigo do leilao. Obrigatorio (asterisco no diagrama). */
    @NotNull(message = "O codigo do leilao e obrigatorio.")
    private Integer codigo;

    /** Descricao. Tamanho maximo 80. */
    @Size(max = 80, message = "A descricao deve ter no maximo 80 caracteres.")
    private String descricao;

    /** Id da empresa vendedora. Obrigatorio (asterisco no diagrama). */
    @NotNull(message = "O vendedor e obrigatorio.")
    private Integer vendedor;

    /** Data e hora previstas para o inicio do leilao. */
    private LocalDateTime inicioPrevisto;
}
