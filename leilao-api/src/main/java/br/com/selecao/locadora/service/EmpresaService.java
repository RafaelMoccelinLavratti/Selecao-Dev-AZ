package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.EmpresaBO;
import br.com.selecao.locadora.dto.EmpresaRequest;
import br.com.selecao.locadora.dto.EmpresaResponse;
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
 *  CONTROLADOR REST: EMPRESA
 * ============================================================================
 *
 *  MESMO PADRAO DO CONTROLADOR DE UNIDADE, com uma diferenca importante:
 *  aqui o DTO de entrada tem MUITAS validacoes de formato (CNPJ, CEP,
 *  telefone, e-mail, site, tamanhos maximos). E o que atende a exigencia
 *  do EXERCICIO 8 ("validar as mascaras e os formatos de numero, email,
 *  url e tamanho maximo do texto").
 *
 *  A validacao acontece em DOIS lugares, de proposito:
 *    - no BACKEND (aqui, via @Valid): e a garantia. Qualquer cliente
 *      que chamar a API passa por ela.
 *    - no FRONTEND (tela de cadastro): e a conveniencia. O usuario ve o
 *      erro na hora, sem perder o que ja digitou.
 *
 *  OBSERVACAO DE SEGURANCA:
 *  A senha recebida aqui e convertida em hash BCrypt na camada de
 *  negocio, e NUNCA volta na resposta (veja EmpresaResponse).
 * ============================================================================
 */
@RestController
@RequestMapping(value = {"/empresas", "/empresa"})
public class EmpresaService {

    @Autowired
    private EmpresaBO empresaBO;

    /**
     * GET /empresas - lista todas as empresas.
     * E o que a tela do exercicio 6 consome.
     */
    @GetMapping
    public ResponseEntity<List<EmpresaResponse>> buscarTodos() {
        return ResponseEntity.ok(empresaBO.buscarTodos());
    }

    /**
     * GET /empresas/{id} - busca uma empresa pelo id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(empresaBO.buscarPorId(id));
    }

    /**
     * POST /empresas - inclui uma nova empresa.
     */
    @PostMapping
    public ResponseEntity<EmpresaResponse> incluir(@Valid @RequestBody EmpresaRequest request) {
        EmpresaResponse resposta = empresaBO.salvar(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /empresas/{id} - altera a empresa do id informado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> alterar(@PathVariable Integer id,
                                                    @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(empresaBO.salvar(request, id));
    }

    /**
     * DELETE /empresas/{id} - remove a empresa do id informado.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        empresaBO.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
