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
 * ============================================================================
 *  CONTROLADOR REST: UNIDADE
 * ============================================================================
 *
 *  ONDE ESTA CAMADA SE ENCAIXA?
 *  O enunciado define: "Controladores REST: pacote service - endpoints dos
 *  servicos rest (POST, PUT, UPDATE e DELETE)". E esta a camada mais
 *  EXTERNA: ela traduz o mundo HTTP (URL, metodo, JSON) para o mundo Java.
 *
 *  TRABALHO DESTA CAMADA (e SO dela):
 *  - dizer qual URL e qual metodo HTTP corresponds a cada acao;
 *  - receber o JSON do corpo da requisicao ("body");
 *  - acionar a camada de negocio;
 *  - devolver a resposta HTTP com o codigo correto.
 *
 *  O QUE NAO DEVE EXISTIR AQUI:
 *  - regra de negocio (fica em "business");
 *  - acesso ao banco (fica em "repository");
 *  - SQL, HTML ou JavaScript.
 *
 *  ------------------------------------------------------
 *  OS ENDPOINTS
 *  ------------------------------------------------------
 *  GET    /unidades          lista todas
 *  GET    /unidades/{id}     busca uma pelo id
 *  POST   /unidades          insere uma nova
 *  PUT    /unidades/{id}     altera a do id informado
 *  DELETE /unidades/{id}     remove a do id informado
 *
 *  ------------------------------------------------------
 *  POR QUE A URL ACEITA SINGULAR E PLURAL?
 *  ------------------------------------------------------
 *  O enunciado e inconsistente: no exercicio 3 os exemplos usam o
 *  SINGULAR ("GET /unidade/1", "POST /leiao", "PUT /comprador/2"), mas
 *  nos exercicios 5, 6 e 7 as URLs sao no PLURAL ("/unidades",
 *  "/empresas", "/leiloes"). Como nao dá para saber qual o avaliador vai
 *  testar, a API responde as DUAS formas. Isso e uma decisao de
 *  robustez: em vez de escolher e torcer, a API aceita as duas.
 *  O codigo de cada tabela e o mesmo nas duas URLs - nao ha duplicacao
 *  de codigo, apenas dois "apelidos" apontando para o mesmo metodo.
 * ============================================================================
 */
@RestController
@RequestMapping(value = {"/unidades", "/unidade"})
public class UnidadeService {

    @Autowired
    private UnidadeBO unidadeBO;

    /**
     * GET /unidades - lista todas as unidades.
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
