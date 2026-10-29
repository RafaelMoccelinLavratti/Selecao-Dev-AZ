package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.LoteBO;
import br.com.selecao.locadora.dto.LoteRequest;
import br.com.selecao.locadora.dto.LoteResponse;
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
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * ============================================================================
 *  CONTROLADOR REST: LOTE
 * ============================================================================
 *
 *  Alem do CRUD padrao, existe um endpoint extra:
 *  GET /lotes/leilao/{leilaoId} - lista os lotes de um leilao especifico.
 *  Ele existe porque e assim que a aplicacao monta o total do leilao.
 * ============================================================================
 */
@RestController
@RequestMapping(value = {"/lotes", "/lote"})
public class LoteService {

    @Autowired
    private LoteBO loteBO;

    /**
     * GET /lotes - lista todos os lotes.
     *
     * @return a lista de lotes
     */
    @GetMapping
    public ResponseEntity<List<LoteResponse>> buscarTodos() {
        return ResponseEntity.ok(loteBO.buscarTodos());
    }

    /**
     * GET /lotes/leilao/{leilaoId} - lista os lotes de um leilao.
     *
     * @param leilaoId id do leilao
     * @return os lotes desse leilao
     */
    @GetMapping("/leilao/{leilaoId}")
    public ResponseEntity<List<LoteResponse>> buscarPorLeilao(@PathVariable Integer leilaoId) {
        return ResponseEntity.ok(loteBO.buscarPorLeilao(leilaoId));
    }

    /**
     * GET /lotes/{id} - busca um lote pelo id.
     *
     * @param id id do lote
     * @return o lote encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<LoteResponse> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(loteBO.buscarPorId(id));
    }

    /**
     * POST /lotes - inclui um novo lote.
     *
     * @param request dados do novo lote
     * @return o lote criado
     */
    @PostMapping
    public ResponseEntity<LoteResponse> incluir(@Valid @RequestBody LoteRequest request) {
        LoteResponse resposta = loteBO.salvar(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /lotes/{id} - altera o lote do id informado.
     *
     * @param id      id do lote a alterar
     * @param request novos dados
     * @return o lote alterado
     */
    @PutMapping("/{id}")
    public ResponseEntity<LoteResponse> alterar(@PathVariable Integer id,
                                                @Valid @RequestBody LoteRequest request) {
        return ResponseEntity.ok(loteBO.salvar(request, id));
    }

    /**
     * DELETE /lotes/{id} - remove o lote do id informado.
     *
     * @param id id do lote a remover
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        loteBO.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
