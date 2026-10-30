package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.UnidadeBO;
import br.com.selecao.locadora.dto.UnidadeRequest;
import br.com.selecao.locadora.dto.UnidadeResponse;
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
 * Controller REST da unidade.
 *
 * GET    /unidades          lista todas
 * GET    /unidades/{id}     busca uma pelo id
 * POST   /unidades          insere uma nova
 * PUT    /unidades/{id}     altera a do id informado
 * DELETE /unidades/{id}     remove a do id informado
 *
 * A URL responde no singular e no plural porque o enunciado e inconsistente
 * entre os exercicios 3 e 5-7. Sao os mesmos metodos, com dois apelidos -
 * nao ha codigo duplicado.
 */
@RestController
@RequestMapping(value = {"/unidades", "/unidade"})
public class UnidadeService {

    @Autowired
    private UnidadeBO unidadeBO;

    /**
     * GET /unidades - lista todas as unidades.
     *
     * O codigo 200 (OK) e o esperado. Se a lista vier vazia, ainda e
     * 200 - uma lista vazia e um resultado valido, nao um erro.
     *
     * @return a lista de unidades
     */
    @GetMapping
    public ResponseEntity<List<UnidadeResponse>> buscarTodos() {
        return ResponseEntity.ok(unidadeBO.buscarTodos());
    }

    /**
     * GET /unidades/{id} - busca uma unidade pelo id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UnidadeResponse> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(unidadeBO.buscarPorId(id));
    }

    /**
     * POST /unidades - insere uma nova unidade.
     *
     * O "@Valid" e o que dispara a validacao automatica dos campos
     * declarados no DTO (tamanho maximo, obrigatoriedade...). Se algum
     * campo estiver invalido, o Spring lanca a excecao de validacao e o
     * GlobalExceptionHandler devolve HTTP 400 com a lista dos problemas.
     */
    @PostMapping
    public ResponseEntity<UnidadeResponse> incluir(@Valid @RequestBody UnidadeRequest request) {
        UnidadeResponse resposta = unidadeBO.salvar(request, null);
        // HTTP 201 Created: informa que um recurso novo foi criado.
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /unidades/{id} - altera a unidade do id informado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UnidadeResponse> alterar(@PathVariable Integer id,
                                                    @Valid @RequestBody UnidadeRequest request) {
        return ResponseEntity.ok(unidadeBO.salvar(request, id));
    }

    /**
     * DELETE /unidades/{id} - remove a unidade do id informado.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        unidadeBO.excluir(id);
        // HTTP 204 No Content: sucesso, sem corpo de resposta.
        return ResponseEntity.noContent().build();
    }
}
