package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Corpo do POST /auth/login.
 *
 * So dois campos: usuario e senha. A entidade Empresa tem quinze, e aceitar
 * a entidade inteira abriria a porta para o cliente gravar campos que nao
 * deveria.
 *
 * O @Size no usuario e um limite de entrada, para nao aceitar um usuario de
 * megabytes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Usuario do sistema.
     */
    @NotBlank(message = "O usuario e obrigatorio.")
    @Size(max = 100, message = "O usuario deve ter no maximo 100 caracteres.")
    private String usuario;

    /**
     * Senha do usuario.
     */
    @NotBlank(message = "A senha e obrigatoria.")
    @Size(min = 8, max = 128, message = "A senha deve ter entre 8 e 128 caracteres.")
    private String senha;
}
