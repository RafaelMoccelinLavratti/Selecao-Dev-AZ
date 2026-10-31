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
    @NotBlank(message = "O usuário é obrigatório.")
    @Size(max = 100, message = "O usuário deve ter no máximo 100 caracteres.")
    private String usuario;

    /**
     * Senha do usuario.
     *
     * Aqui nao ha "@Size(min = 8)". A regra de senha minima pertence ao
     * CADASTRO (o "EmpresaRequest"), onde a senha e' escolhida. No
     * login, a senha ja' existe: o que o servidor faz e' compara-la com
     * o hash guardado.
     *
     * Impor o minimo aqui quebraria contas legitimas - e' o que
     * acontecia com a senha "Senha@1" da massa de dados (7 caracteres):
     * o login devolvia HTTP 400 sem chegar a conferir a senha, e a
     * pessoa nunca conseguia entrar, mesmo tendo a senha certa.
     *
     * O limite maximo continua valendo, para nao aceitar um corpo de
     * requisicao de megabytes.
     */
    @NotBlank(message = "A senha é obrigatória.")
    @Size(max = 128, message = "A senha deve ter no máximo 128 caracteres.")
    private String senha;
}
