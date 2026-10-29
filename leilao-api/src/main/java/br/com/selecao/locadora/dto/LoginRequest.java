package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * ============================================================================
 *  DTO: PEDIDO DE AUTENTICACAO
 * ============================================================================
 *
 *  E o corpo do POST /auth/login.
 *
 *  ------------------------------------------------------
 *  POR QUE UM DTO E NAO A ENTIDADE?
 *  ------------------------------------------------------
 *  Porque o DTO e a FRONTEIRA entre o mundo HTTP e o mundo do negocio.
 *  A entidade "Empresa" tem quinze campos; este DTO tem dois. Aceitar a
 *  entidade inteira permitiria que o cliente mandasse, por exemplo, o
 *  "id" e o "createdAt" - e o servidor gravaria o que o cliente pediu.
 *
 *  No caso da autenticacao isso e ainda mais importante: o DTO garante
 *  que NENHUM campo alem de usuario e senha entre na aplicacao. Nao ha
 *  "campo a mais" que um atacante possa usar para influenciar a
 *  autenticacao.
 *
 *  ------------------------------------------------------
 *  A VALIDACAO DE TAMANHO
 *  ------------------------------------------------------
 *  O "@Size(max = ...)" no usuario e um limite de ENTRADA, nao uma
 *  validacao de senha. Sem ele, um cliente poderia mandar um usuario de
 *  10 megabytes, e o servidor gastaria memoria e tempo de CPU comparando
 *  uma string gigante com o hash do banco.
 *
 *  A senha tem "@Size(min = 8, max = 128)": 128 e o tamanho maximo
 *  aceito pelo BCrypt e pelo Argon2. Acima disso, o algoritmo ignora o
 *  resto - ou, pior, um codigo com truncamento silencioso daria origem
 *  a senhas equivalentes que o usuario nao espera.
 * ============================================================================
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
