package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.CompradorBO;
import br.com.selecao.locadora.dto.CompradorRequest;
import br.com.selecao.locadora.dto.CompradorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * ============================================================================
 *  CONTROLADOR REST: COMPRADOR
 * ============================================================================
 *
 *  A DIFFERENCA DOS OUTROS: a chave primaria aqui e COMPOSTA
 *  (empresa + leilao), entao nao existe um "/{id}" unico.
 *
 *  ------------------------------------------------------
 *  OS ENDPOINTS
 *  ------------------------------------------------------
 *  GET    /compradores                          lista todos
 *  GET    /compradores/{empresaId}/{leilaoId}   busca um vinculo
 *  GET    /compradores?empresa=X&leilao=Y      busca um vinculo (outra forma)
 *  POST   /compradores                          cria um vinculo
 *  PUT    /compradores/{empresaId}/{leilaoId}   altera o vinculo
 *  DELETE /compradores/{empresaId}/{leilaoId}   remove o vinculo
 *
 *  Oferecer as DUAS formas de consulta (caminho e query string) e uma
 *  escolha de robustez: qualquer um dos dois jeitos de chamar funciona.
 * ============================================================================
 */
@RestController
@RequestMapping(value = {"/compradores", "/comprador"})
public class CompradorService {

    @Autowired
    private CompradorBO compradorBO;

    /**
     * GET /compradores - lista todos os vinculos.
     */
    @GetMapping
    public ResponseEntity<List<CompradorResponse>> buscarTodos() {
        return ResponseEntity.ok(compradorBO.buscarTodos());
    }

    /**
     * GET /compradores/{empresaId}/{leilaoId} - busca um vinculo.
     */
    @GetMapping("/{empresaId}/{leilaoId}")
    public ResponseEntity<CompradorResponse> buscarPorId(@PathVariable Integer empresaId,
                                                          @PathVariable Integer leilaoId) {
        return ResponseEntity.ok(compradorBO.buscarPorId(empresaId, leilaoId));
    }

    /**
     * GET /compradores?empresa=X&leilao=Y - mesma consulta, outra forma.
     *
     * Se o chamador enviar apenas um dos dois parametros, a API devolve
     * HTTP 400 com uma mensagem explicando o que falta.
     */
    @GetMapping(params = {"empresa", "leilao"})
    public ResponseEntity<CompradorResponse> buscarPorParametros(@RequestParam Integer empresa,
                                                                  @RequestParam Integer leilao) {
        return ResponseEntity.ok(compradorBO.buscarPorId(empresa, leilao));
    }

    /**
     * POST /compradores - registra a participacao de uma empresa.
     */
    @PostMapping
    public ResponseEntity<CompradorResponse> incluir(@Valid @RequestBody CompradorRequest request) {
        CompradorResponse resposta = compradorBO.salvar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /compradores/{empresaId}/{leilaoId} - altera o vinculo.
     */
    @PutMapping("/{empresaId}/{leilaoId}")
    public ResponseEntity<CompradorResponse> alterar(@PathVariable Integer empresaId,
                                                      @PathVariable Integer leilaoId,
                                                      @Valid @RequestBody CompradorRequest request) {
        return ResponseEntity.ok(compradorBO.atualizar(empresaId, leilaoId, request));
    }

    /**
     * DELETE /compradores/{empresaId}/{leilaoId} - remove o vinculo.
     */
    @DeleteMapping("/{empresaId}/{leilaoId}")
    public ResponseEntity<Void> excluir(@PathVariable Integer empresaId, @PathVariable Integer leilaoId) {
        compradorBO.excluir(empresaId, leilaoId);
        return ResponseEntity.noContent().build();
    }
}
