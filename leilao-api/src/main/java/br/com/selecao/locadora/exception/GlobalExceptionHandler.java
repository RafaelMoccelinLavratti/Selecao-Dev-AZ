package br.com.selecao.locadora.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento global de erros.
 *
 * Sem isso, uma excecao inesperada devolveria uma pagina HTML com a pilha
 * de execucao. Cada tipo de excecao tem o seu metodo, e a resposta segue
 * sempre no mesmo formato.
 *
 * O cliente recebe o que houve (qual campo falhou e por que) e nunca o como
 * (stack trace, SQL, nomes de classes).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Erro de validacao dos dados de entrada (Bean Validation).
     * Resultado: HTTP 400 com a lista de campos invalidos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            campos.put(erro.getField(), erro.getDefaultMessage());
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", HttpStatus.BAD_REQUEST.value());
        corpo.put("erro", "Dados invalidos");
        corpo.put("mensagem", "Verifique os campos destacados.");
        corpo.put("campos", campos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    /**
     * Registro pedido nao existe -> HTTP 404.
     */
    @ExceptionHandler(RegistroNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> tratarNaoEncontrado(RegistroNaoEncontradoException ex) {
        return montarErro(HttpStatus.NOT_FOUND, "Registro nao encontrado", ex.getMessage());
    }

    /**
     * Credencial invalida (senha errada, token vencido, token forjado)
     * -> HTTP 401.
     *
     * A mensagem devolvida e sempre a mesma, qualquer que seja o motivo
     * real (usuario inexistente, senha errada, token expirado, token
     * adulterado). Isso e de proposito: se o servidor respondesse "usuario
     * nao encontrado" num caso e "senha incorreta" em outro, um atacante
     * descobriria quais usuarios existem - basta tentar logins e olhar o
     * texto da resposta.
     */
    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<Map<String, Object>> tratarTokenInvalido(TokenInvalidoException ex) {
        return montarErro(HttpStatus.UNAUTHORIZED, "Nao autorizado", ex.getMessage());
    }

    /**
     * Regra de negocio violada -> HTTP 409 (conflito).
     */
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> tratarRegraNegocio(RegraNegocioException ex) {
        return montarErro(HttpStatus.CONFLICT, "Regra de negocio violada", ex.getMessage());
    }

    /**
     * Acesso negado -> HTTP 403 (proibido).
     *
     * A pessoa esta autenticada, mas nao pode tocar no recurso pedido.
     * E o que acontece quando uma empresa tenta ver os leiloes de outra
     * ou o historico de auditoria, que e do administrador.
     */
    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Map<String, Object>> tratarAcessoNegado(AcessoNegadoException ex) {
        return montarErro(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage());
    }

    /**
     * Tipo de parametro invalido (ex.: "abc" no lugar de um id numerico)
     * -> HTTP 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> treatableTipo(MethodArgumentTypeMismatchException ex) {
        return montarErro(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "O valor '" + ex.getValue() + "' nao e valido para o parametro '" + ex.getName() + "'.");
    }

    /**
     * Corpo da requisicao que nao bate com o que o DTO espera -> HTTP 400.
     *
     * Exemplo: mandar "vendedor": "Metalurgica Aurora Ltda" num campo que
     * e' o ID da empresa. O Jackson lanca a excecao de conversao antes do
     * controller, e sem este tratamento ela cairia no handler generico
     * e voltaria como 500 - o que e' errado: nao foi erro do servidor,
     * foi o cliente mandando dado invalido.
     *
     * A diferenca importa para quem consome a API: um 400 diz "corrige o
     * teu dado"; um 500 diz "tenta de novo depois" e faz o cliente
     * repetir a mesma requisição, que vai falhar igual.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> tratarCorpoInvalido(HttpMessageNotReadableException ex) {
        // A mensagem do Jackson cita o campo, mas em ingles e com
        // detalhes de implementacao. A mensagem daqui fala o que
        // importa para quem consome a API.
        String campo = extrairCampo(ex);

        return montarErro(HttpStatus.BAD_REQUEST, "Dados invalidos",
                campo != null
                        ? "O campo '" + campo + "' tem um valor invalido. Confira o tipo do dado enviado."
                        : "O corpo da requisicao tem um valor invalido. Confira os dados enviados.");
    }

    /**
     * Descobre qual campo do corpo falhou, a partir da mensagem do
     * Jackson.
     *
     * O Jackson escreve algo como:
     *     JSON parse error: Cannot deserialize value of type `java.lang.Integer`
     *     from String "Metalurgica Aurora Ltda": not a valid `Integer` value
     *
     * O nome do campo vem antes, na forma "through reference chain".
     */
    private String extrairCampo(HttpMessageNotReadableException ex) {
        Throwable causa = ex.getCause();

        if (causa instanceof InvalidFormatException) {
            // O caminho do campo vem na mensagem do proprio Jackson.
            String mensagem = causa.getMessage();
            if (mensagem != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("through reference chain: .*?\\[([^\\]]+)\\]")
                        .matcher(mensagem);

                if (m.find()) {
                    // O Jackson devolve o campo com aspas: "\"vendedor\"".
                    // As aspas nao ajudam quem le a mensagem.
                    return m.group(1).replace("\"", "");
                }
            }
        }

        return null;
    }

    /**
     * URL inexistente -> HTTP 404.
     *
     * Pra este tratamento entrar em acao, o Spring precisa estar
     * configurado pra lancar a excecao quando nao encontra nenhum
     * controller (em vez de tentar servir um arquivo estatico). Isso e
     * feito em application.properties:
     *     spring.mvc.throw-exception-if-no-handler-found=true
     *     spring.web.resources.add-mappings=false
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> tratarRecursoNaoEncontrado(NoHandlerFoundException ex) {
        return montarErro(HttpStatus.NOT_FOUND, "Recurso nao encontrado", "O recurso solicitado nao existe.");
    }

    /**
     * Erro inesperado -> HTTP 500 com mensagem generica.
     *
     * O detalhe real da excecao nao vai pra resposta: ele vai pro log do
     * servidor (via SLF4J), onde fica disponivel pra equipe de TI. O
     * cliente recebe apenas "erro interno". E a diferenca entre um
     * sistema seguro e um que entrega um "mapa do sistema" pra qualquer
     * pessoa na internet.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarErroInesperado(Exception ex) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        log.error("Erro inesperado ao processar a requisicao", ex);
        return montarErro(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.");
    }

    /**
     * Monta o corpo padrao das respostas de erro, sempre no mesmo formato.
     * Um formato unico facilita o consumo pelo frontend (uma unica regra
     * de leitura do campo "mensagem").
     */
    private ResponseEntity<Map<String, Object>> montarErro(HttpStatus status, String erro, String mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", status.value());
        corpo.put("erro", erro);
        corpo.put("mensagem", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }
}
