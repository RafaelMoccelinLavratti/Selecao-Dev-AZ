package br.com.selecao.locadora.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 *  TRATAMENTO GLOBAL DE ERROS  (@RestControllerAdvice)
 * ============================================================================
 *
 *  PARA QUE SERVE?
 *  Para que NENHUM erro "vaze" cru para o navegador. Sem isso, uma excecao
 *  inesperada devolve uma pagina HTML com a pilha de execucao (stack trace),
 *  que revela nomes de classes, caminhos de arquivos, versoes de framework e
 *  ate trechos de SQL. Isso e vazamento de informacao - uma falha comum e
 *  grave de seguranca da informacao.
 *
 *  COMO FUNCIONA:
 *  O Spring chama o metodo @ExceptionHandler correspondente ao tipo da
 *  excecao e usa o retorno como resposta HTTP. Nos controllers usamos
 *  "@Valid @RequestBody", e quando a validacao falha o Spring lanca
 *  MethodArgumentNotValidException - que e tratada aqui, devolvendo HTTP 400
 *  com a lista exata dos campos invalidos e seus motivos.
 *
 *  REGRA DE OURO DA RESPOSTA DE ERRO:
 *  - O cliente recebe o QUÊ (qual campo falhou e por que).
 *  - O cliente NAO recebe o COMO (stack trace, SQL, nomes de classes).
 *  - O "como" fica apenas no log do servidor, onde a equipe de TI pode usar.
 * ============================================================================
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
     * Regra de negocio violada -> HTTP 409 (conflito).
     */
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> tratarRegraNegocio(RegraNegocioException ex) {
        return montarErro(HttpStatus.CONFLICT, "Regra de negocio violada", ex.getMessage());
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
     * URL inexistente -> HTTP 404.
     *
     * Para este tratamento entrar em acao, o Spring precisa estar
     * configurado para LANÇAR a excecao quando nao encontra nenhum
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
     * Erro inesperado -> HTTP 500 com mensagem GENERICA.
     *
     *  IMPORTANTE: o detalhe real da excecao NAO vai para a resposta.
     *  Ela vai para o log do servidor (via SLF4J), onde fica disponivel
     * para a equipe de TI. O cliente recebe apenas "erro interno".
     *  Essa e a diferenca entre um sistema seguro e um que entrega a
     *  "mapa do sistema" para qualquer pessoa na internet.
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
     * Formato unico facilita o consumo pelo frontend (uma unica regra de
     * leitura do campo "mensagem").
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
