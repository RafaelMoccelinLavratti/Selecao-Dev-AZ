package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.AutorizacaoBO;
import br.com.selecao.locadora.business.LeilaoBO;
import br.com.selecao.locadora.config.UsuarioAtual;
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
 * Controller REST do leilao.
 *
 * O GET /leiloes devolve, alem dos dados, a razao social do vendedor e o
 * total do leilao - que e o que o exercicio 7 pede.
 */
@RestController
@RequestMapping(value = {"/leiloes", "/leilao"})
public class LeilaoService {

    @Autowired
    private LeilaoBO leilaoBO;

    /**
     * Regras de acesso.
     */
    @Autowired
    private AutorizacaoBO autorizacaoBO;

    /**
     * GET /leiloes - lista os leiloes COM o total calculado e a razao
     * social do vendedor. E o endpoint da tela do exercicio 7.
     *
     * O administrador ve todos. O usuario de uma empresa ve os leiloes
     * que ela vende e os leiloes em que esta inscrita como compradora -
     * o filtro vem do banco, nao de um "if" depois de trazer tudo.
     */
    @GetMapping
    public ResponseEntity<List<LeilaoResumoDTO>> buscarTodos() {

        // O escopo nulo significa "sem restricao": administrador ou
        // perfil de desenvolvimento. Nos dois casos, a lista completa.
        Integer escopo = UsuarioAtual.escopoEmpresa();

        if (escopo == null) {
            return ResponseEntity.ok(leilaoBO.buscarResumo());
        }

        return ResponseEntity.ok(leilaoBO.buscarResumoParaEmpresa(escopo));
    }

    /**
     * GET /leiloes/{id} - busca um leilao pelo id.
     *
     * O usuario de uma empresa so acessa os leiloes em que tem
     * participacao (vende ou compra).
     */
    @GetMapping("/{id}")
    public ResponseEntity<LeilaoResponse> buscarPorId(@PathVariable Integer id) {
        autorizacaoBO.exigirLeituraLeilao(id);
        return ResponseEntity.ok(leilaoBO.buscarPorId(id));
    }

    /**
     * POST /leiloes - inclui um novo leilao.
     *
     * O administrador cria leiloes para qualquer empresa. O usuario de
     * uma empresa so cria leilao em que ela e' a vendedora - um leilao
     * com o vendedor de outra empresa seria um leilao pirata.
     *
     * @param request dados do novo leilao
     * @return o leilao criado
     */
    @PostMapping
    public ResponseEntity<LeilaoResponse> incluir(@Valid @RequestBody LeilaoRequest request) {

        if (!UsuarioAtual.ehAdministrador()) {
            autorizacaoBO.exigirProprioOuAdministrador(request.getVendedor());
        }

        LeilaoResponse resposta = leilaoBO.salvar(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /leiloes/{id} - altera o leilao do id informado.
     *
     * So o vendedor (ou o administrador) altera o proprio leilao.
     *
     * E a alteracao tambem nao pode trocar o vendedor: quem tem o
     * leilao continua sendo a unica empresa que pode mexer nele.
     */
    @PutMapping("/{id}")
    public ResponseEntity<LeilaoResponse> alterar(@PathVariable Integer id,
                                                    @Valid @RequestBody LeilaoRequest request) {
        autorizacaoBO.exigirEscritaLeilaoComVendedor(id, request.getVendedor());
        return ResponseEntity.ok(leilaoBO.salvar(request, id));
    }

    /**
     * DELETE /leiloes/{id} - remove o leilao do id informado.
     *
     * So o vendedor (ou o administrador) exclui o proprio leilao.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        autorizacaoBO.exigirEscritaLeilao(id);
        leilaoBO.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
