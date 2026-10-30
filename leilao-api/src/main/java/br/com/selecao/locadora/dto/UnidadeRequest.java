package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * DTO de entrada da unidade.
 *
 * O que chega da internet e dado nao confiavel. Usando a entidade direto,
 * um cliente poderia mandar o id e trocar a chave de um registro; o DTO
 * lista os campos que podem ser gravados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeRequest {

    /**
     * Nome da unidade. Obrigatorio (asterisco no diagrama) e com no
     * maximo de 128 caracteres (tamanho da coluna no banco).
     */
    @NotBlank(message = "O nome da unidade e obrigatorio.")
    @Size(max = 128, message = "O nome da unidade deve ter no maximo 128 caracteres.")
    private String nome;
}
