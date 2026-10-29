package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.LeilaoBO;
import br.com.selecao.locadora.dto.LeilaoRequest;
import br.com.selecao.locadora.dto.LeilaoResponse;
import br.com.selecao.locadora.dto.LeilaoResumoDTO;
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
 *  CONTROLADOR REST: LEILAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  UM DETALHE IMPORTANTE DO EXERCICIO 7
 *  ------------------------------------------------------
 *  O exercicio 7 pede que GET /leiloes traga:
 *      - razao social do VENDEDOR
 *      - inicio previsto
 *      - TOTAL do leilao (soma de quantidade * valorInicial dos lotes)
 *
 *  Esse endpoint devolve o "resumo" (LeilaoResumoDTO), ja com o total
 *  calculado pelo banco de dados. Alem dele, o CRUD completo da tabela
 *  tb_leilao existe nos demais endpoints, como pede o exercicio 3.
 * ============================================================================
 */
@RestController
@RequestMapping(value = {"/leiloes", "/leilao"})
public class LeilaoService {

    @Autowired
    private LeilaoBO leilaoBO;

    /**
     * GET /leiloes - lista os leiloes COM o total calculado e a razao
     * social do vendedor. E o endpoint da tela do exercicio 7.
     */
    @GetMapping
    public ResponseEntity<List<LeilaoResumoDTO>> buscarTodos() {
        return ResponseEntity.ok(leilaoBO.buscarResumo());
    }

    /**
     * GET /leiloes/{id} - busca um leilao pelo id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LeilaoResponse> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(leilaoBO.buscarPorId(id));
    }

    /**
     * POST /leiloes - inclui um novo leilao.
     *
     * @param request dados do novo leilao
     * @return o leilao criado
     */
    @PostMapping
    public ResponseEntity<LeilaoResponse> incluir(@Valid @RequestBody LeilaoRequest request) {
        LeilaoResponse resposta = leilaoBO.salvar(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /leiloes/{id} - altera o leilao do id informado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<LeilaoResponse> alterar(@PathVariable Integer id,
                                                    @Valid @RequestBody LeilaoRequest request) {
        return ResponseEntity.ok(leilaoBO.salvar(request, id));
    }

    /**
     * DELETE /leiloes/{id} - remove o leilao do id informado.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        leilaoBO.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
