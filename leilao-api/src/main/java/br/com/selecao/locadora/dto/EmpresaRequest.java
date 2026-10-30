package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * DTO de entrada da empresa.
 *
 * A validacao mora aqui e nao so no frontend: qualquer um pode chamar a API
 * sem passar pela tela, e o backend que nao pode ser contornado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaRequest {

    /**
     * Razao social. Obrigatoria (asterisco no diagrama).
     * @NotBlank = nao pode ser vazio nem apenas espacos.
     * @Size    = respeita o tamanho maximo da coluna (84).
     */
    @NotBlank(message = "A razao social e obrigatoria.")
    @Size(max = 84, message = "A razao social deve ter no maximo 84 caracteres.")
    private String razaoSocial;

    /**
     * CNPJ. Obrigatorio e unico (asterisco + sublinhado no diagrama).
     * O padrao aceita os dois formatos, com e sem pontuacao.
     */
    @NotBlank(message = "O CNPJ e obrigatorio.")
    @Pattern(
            regexp = "^(\\d{14}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})$",
            message = "CNPJ invalido. Use 14 digitos ou o formato 00.000.000/0000-00.")
    private String cnpj;

    /** Logradouro. Tamanho maximo 84 (igual a coluna). */
    @Size(max = 84, message = "O logradouro deve ter no maximo 84 caracteres.")
    private String logadouro;

    /** Municipio. Tamanho maximo 64. */
    @Size(max = 64, message = "O municipio deve ter no maximo 64 caracteres.")
    private String municipio;

    /** Numero do endereco. Tamanho maximo 10. */
    @Size(max = 10, message = "O numero deve ter no maximo 10 caracteres.")
    private String numero;

    /** Complemento do endereco. Tamanho maximo 84. */
    @Size(max = 84, message = "O complemento deve ter no maximo 84 caracteres.")
    private String complemento;

    /** Quantidade. Nao pode ser negativa (a coluna tem CHECK no banco). */
    @Min(value = 0, message = "A quantidade nao pode ser negativa.")
    private Integer quantidade;

    /** CEP: 8 digitos, com ou sem traco. */
    @Pattern(
            regexp = "^\\d{5}-?\\d{3}$",
            message = "CEP invalido. Use 00000-000.")
    private String cep;

    /** Telefone: (00) 0000-0000, (00) 00000-0000 ou so digitos. */
    @Pattern(
            regexp = "^(\\(\\d{2}\\)\\s?)?\\d{4,5}-?\\d{4}$",
            message = "Telefone invalido. Use (00) 0000-0000.")
    private String telefone;

    /** E-mail. @Email valida o formato; @Size respeita a coluna (254). */
    @Email(message = "E-mail invalido.")
    @Size(max = 254, message = "O e-mail deve ter no maximo 254 caracteres.")
    private String email;

    /** Site. Precisa comecar com http:// ou https://. */
    @Pattern(
            regexp = "^https?://.+",
            message = "Site invalido. Deve comecar com http:// ou https://")
    @Size(max = 254, message = "O site deve ter no maximo 254 caracteres.")
    private String site;

    /** Nome de usuario para login. Unico no banco. Tamanho maximo 20. */
    @Size(max = 20, message = "O usuario deve ter no maximo 20 caracteres.")
    private String usuario;

    /**
     * Senha. Nunca e devolvida na resposta da API e nunca e gravada em
     * texto puro: a camada de negocio guarda apenas o hash.
     * Tamanho minimo de 8 caracteres - menos que isso e triviao de quebrar.
     */
    @Size(min = 8, max = 128, message = "A senha deve ter entre 8 e 128 caracteres.")
    private String senha;

    /**
     * Ajuste fino na senha.
     *
     * Este setter e escrito a mao (o Lombok nao gera um porque ja existe
     * um) e converte uma senha em branco em null.
     *
     * Por que isso e necessario?
     * Quando o usuario edita uma empresa e nao troca a senha, o formulario
     * envia o campo vazio. O @Size(min = 8) rejeitaria uma string vazia
     * ("") - e so ignoraria null. Resultado: o usuario nao conseguiria
     * salvar uma simples alteracao de e-mail sem antes digitar a senha.
     *
     * Convertendo "" em null logo na desserializacao, o comportamento fica:
     * senha vazia = "nao quero trocar a senha" (e a empresa mantem o hash
     * que ja tinha). Uma senha realmente curta (com menos de 8 caracteres)
     * continua sendo rejeitada com a mensagem de erro.
     *
     * O Java chama o setter durante a leitura do JSON, e a validacao
     * acontece depois - por isso a conversao acontece a tempo.
     */
    public void setSenha(String senha) {
        this.senha = (senha == null || senha.trim().isEmpty()) ? null : senha;
    }
}
