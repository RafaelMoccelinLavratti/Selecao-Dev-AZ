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
    @NotBlank(message = "A razão social é obrigatória.")
    @Size(max = 84, message = "A razão social deve ter no máximo 84 caracteres.")
    private String razaoSocial;

    /**
     * CNPJ. Obrigatorio e unico (asterisco + sublinhado no diagrama).
     * O padrao aceita os dois formatos, com e sem pontuacao.
     */
    @NotBlank(message = "O CNPJ é obrigatório.")
    @Pattern(
            regexp = "^(\\d{14}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})$",
            message = "CNPJ inválido. Use 14 dígitos ou o formato 00.000.000/0000-00.")
    private String cnpj;

    /** Logradouro. Tamanho maximo 84 (igual a coluna). */
    @Size(max = 84, message = "O logradouro deve ter no máximo 84 caracteres.")
    private String logradouro;

    /** Municipio. Tamanho maximo 64. */
    @Size(max = 64, message = "O município deve ter no máximo 64 caracteres.")
    private String municipio;

    /** Numero do endereco. Tamanho maximo 10. */
    @Size(max = 10, message = "O número deve ter no máximo 10 caracteres.")
    private String numero;

    /** Complemento do endereco. Tamanho maximo 84. */
    @Size(max = 84, message = "O complemento deve ter no máximo 84 caracteres.")
    private String complemento;

    /** Bairro do endereco (campo do diagrama). Tamanho maximo 64. */
    @Size(max = 64, message = "O bairro deve ter no máximo 64 caracteres.")
    private String bairro;

    /** Quantidade. Nao pode ser negativa (a coluna tem CHECK no banco). */
    @Min(value = 0, message = "A quantidade não pode ser negativa.")
    private Integer quantidade;

    /** CEP: 8 digitos, com ou sem traco. */
    @Pattern(
            regexp = "^\\d{5}-?\\d{3}$",
            message = "CEP inválido. Use 00000-000.")
    private String cep;

    /**
     * Telefone: (00) 0000-0000, (00) 00000-0000 ou so digitos.
     *
     * O regex aceita as formas que o usuario digita:
     *
     *   - "1130001100"      (10 digitos: DDD + 8)
     *   - "11300011000"     (11 digitos: DDD + 9)
     *   - "(11) 3000-1100"  (com mascara)
     *   - "113000-1100"     (com traco, sem parenteses)
     *
     * A decomposicao do regex:
     *
     *   (?:\(?\d{2}\)?\s?)?  o DDD, com ou sem parenteses
     *   \d{4,5}             a primeira parte do numero
     *   [- ]?               o separador, opcional
     *   \d{4}               a segunda parte
     *
     * Por que o DDD e' opcional:
     *
     * A API normaliza o telefone ANTES de gravar (tira tudo que nao for
     * digito). Isso quer dizer que a forma que chega no POST pode ser
     * qualquer uma - e o README promete que o telefone e' aceito "so com
     * digitos".
     *
     * O regex antigo exigia o DDD entre parenteses. Com ele, a propria
     * massa de dados era rejeitada: os telefones dela estao em
     * "1130001100" (10 digitos, sem parenteses), e qualquer PUT ou POST
     * de empresa devolvia HTTP 400 - mesmo com todos os outros campos
     * corretos. A API estava mostrando um erro para dados validos.
     *
     * O mesmo regex do frontend (utils/mascaras.js) - as duas pontas
     * aceitam as mesmas formas.
     */
    @Pattern(
            regexp = "^(?:\\(?\\d{2}\\)?\\s?)?\\d{4,5}[- ]?\\d{4}$",
            message = "Telefone inválido. Use (00) 0000-0000.")
    private String telefone;

    /** E-mail. Obrigatorio (asterisco no diagrama). @Email valida o formato; @Size respeita a coluna (254). */
    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "E-mail inválido.")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    private String email;

    /** Site. Precisa comecar com http:// ou https://. */
    @Pattern(
            regexp = "^https?://.+",
            message = "Site inválido. Deve começar com http:// ou https://")
    @Size(max = 254, message = "O site deve ter no máximo 254 caracteres.")
    private String site;

    /** Nome de usuario para login. Obrigatorio e unico no banco. Tamanho maximo 20. */
    @NotBlank(message = "O usuário é obrigatório.")
    @Size(max = 20, message = "O usuário deve ter no máximo 20 caracteres.")
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
