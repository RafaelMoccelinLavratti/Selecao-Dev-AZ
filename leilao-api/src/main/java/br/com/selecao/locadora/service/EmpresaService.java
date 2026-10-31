package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.AutorizacaoBO;
import br.com.selecao.locadora.business.EmpresaBO;
import br.com.selecao.locadora.config.UsuarioAtual;
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
 * Controller REST da empresa.
 *
 * Segue o mesmo padrao do controller de unidade, com uma diferenca: o DTO
 * de entrada tem varias validacoes de formato (CNPJ, CEP, telefone,
 * e-mail).
 *
 * A senha recebida aqui vira hash na camada de negocio e nunca volta na
 * resposta.
 */
@RestController
@RequestMapping(value = {"/empresas", "/empresa"})
public class EmpresaService {

    @Autowired
    private EmpresaBO empresaBO;

    /**
     * Regras de acesso.
     */
    @Autowired
    private AutorizacaoBO autorizacaoBO;

    /**
     * GET /empresas - lista as empresas.
     *
     * O administrador ve todas. O usuario de uma empresa ve apenas a
     * propria: a lista completa traz CNPJ, telefone, e-mail e nome de
     * usuario de todo mundo, e isso e dado de outra empresa.
     *
     * E o que a tela do exercicio 6 consome.
     */
    @GetMapping
    public ResponseEntity<List<EmpresaResponse>> buscarTodos() {

        // O escopo nulo significa "sem restricao": administrador ou
        // perfil de desenvolvimento. Nos dois casos, a lista completa.
        Integer escopo = UsuarioAtual.escopoEmpresa();

        if (escopo == null) {
            return ResponseEntity.ok(empresaBO.buscarTodos());
        }

        return ResponseEntity.ok(empresaBO.buscarTodasDaEmpresa(escopo));
    }

    /**
     * GET /empresas/{id} - busca uma empresa pelo id.
     *
     * O usuario de uma empresa so acessa o proprio cadastro.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> buscarPorId(@PathVariable Integer id) {
        autorizacaoBO.exigirProprioOuAdministrador(id);
        return ResponseEntity.ok(empresaBO.buscarPorId(id));
    }

    /**
     * POST /empresas - inclui uma nova empresa.
     *
     * Cadastrar empresa e' atribuicao do administrador: e' ele que cria
     * o acesso do usuario da empresa nova.
     *
     * O "@Valid" valida o corpo antes do negocio rodar. A senha, por
     * exemplo, tem @Size(min = 8) - se vier menor, o pedido e
     * recusado com HTTP 400.
     *
     * @param request dados da nova empresa
     * @return a empresa criada (sem a senha)
     */
    @PostMapping
    public ResponseEntity<EmpresaResponse> incluir(@Valid @RequestBody EmpresaRequest request) {
        autorizacaoBO.exigirAdministrador("cadastrar empresas");
        EmpresaResponse resposta = empresaBO.salvar(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /empresas/{id} - altera a empresa do id informado.
     *
     * O administrador altera qualquer empresa; o usuario de uma empresa
     * altera apenas a propria.
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> alterar(@PathVariable Integer id,
                                                    @Valid @RequestBody EmpresaRequest request) {
        autorizacaoBO.exigirProprioOuAdministrador(id);
        return ResponseEntity.ok(empresaBO.salvar(request, id));
    }

    /**
     * DELETE /empresas/{id} - remove a empresa do id informado.
     *
     * Exclusao e sempre do administrador: ela leva junto os leiloes e
     * os lotes da empresa.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        autorizacaoBO.exigirAdministrador("excluir empresas");
        empresaBO.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
