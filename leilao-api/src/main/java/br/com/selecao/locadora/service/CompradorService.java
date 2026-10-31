package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.AutorizacaoBO;
import br.com.selecao.locadora.business.CompradorBO;
import br.com.selecao.locadora.config.UsuarioAtual;
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
 * Controller REST do vinculo comprador.
 *
 * A chave primaria e composta (empresa + leilao), entao nao existe um
 * "/{id}" unico: a URL leva os dois ids.
 */
@RestController
@RequestMapping(value = {"/compradores", "/comprador"})
public class CompradorService {

    @Autowired
    private CompradorBO compradorBO;

    /**
     * Regras de acesso.
     */
    @Autowired
    private AutorizacaoBO autorizacaoBO;

    /**
     * GET /compradores - lista todos os vinculos.
     *
     * @return a lista de vinculos empresa-leilao
     */
    @GetMapping
    public ResponseEntity<List<CompradorResponse>> buscarTodos() {

        // O usuario de uma empresa ve apenas as proprias participacoes.
        // A lista completa - o cruzamento de todas as empresas
        // compradoras de todos os leiloes - e' do administrador.
        // O escopo nulo significa "sem restricao": administrador ou
        // perfil de desenvolvimento.
        Integer escopo = UsuarioAtual.escopoEmpresa();

        if (escopo == null) {
            return ResponseEntity.ok(compradorBO.buscarTodos());
        }

        return ResponseEntity.ok(compradorBO.buscarTodosDaEmpresa(escopo));
    }

    /**
     * GET /compradores/{empresaId}/{leilaoId} - busca um vinculo.
     *
     * Como a chave e composta, a URL leva os dois ids.
     *
     * @param empresaId id da empresa
     * @param leilaoId  id do leilao
     * @return o vinculo encontrado
     */
    @GetMapping("/{empresaId}/{leilaoId}")
    public ResponseEntity<CompradorResponse> buscarPorId(@PathVariable Integer empresaId,
                                                          @PathVariable Integer leilaoId) {
        autorizacaoBO.exigirLeituraComprador(empresaId);
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
        autorizacaoBO.exigirLeituraComprador(empresa);
        return ResponseEntity.ok(compradorBO.buscarPorId(empresa, leilao));
    }

    /**
     * POST /compradores - registra a participacao de uma empresa.
     */
    @PostMapping
    public ResponseEntity<CompradorResponse> incluir(@Valid @RequestBody CompradorRequest request) {
        // O usuario de uma empresa so pode se inscrever a si mesmo. O
        // administrador pode vincular qualquer empresa a qualquer leilao.
        autorizacaoBO.exigirProprioOuAdministrador(request.getEmpresa());
        CompradorResponse resposta = compradorBO.salvar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * PUT /compradores/{empresaId}/{leilaoId} - altera o vinculo.
     *
     * Como a chave primaria e o proprio vinculo, "alterar" aqui
     * significa trocar o par - por exemplo, trocar o leilao de destino.
     *
     * @param empresaId id da empresa do vinculo atual
     * @param leilaoId  id do leilao do vinculo atual
     * @param request   empresa e leilao do vinculo novo
     * @return o vinculo novo
     */
    @PutMapping("/{empresaId}/{leilaoId}")
    public ResponseEntity<CompradorResponse> alterar(@PathVariable Integer empresaId,
                                                      @PathVariable Integer leilaoId,
                                                      @Valid @RequestBody CompradorRequest request) {
        // Precisa ser dono do vinculo atual E do vinculo novo: caso
        // contrario, uma empresa poderia se cadastrar em um leilao
        // apenas trocando o id do vinculo antigo.
        autorizacaoBO.exigirProprioOuAdministrador(empresaId);
        autorizacaoBO.exigirProprioOuAdministrador(request.getEmpresa());
        return ResponseEntity.ok(compradorBO.atualizar(empresaId, leilaoId, request));
    }

    /**
     * DELETE /compradores/{empresaId}/{leilaoId} - remove o vinculo.
     *
     * @param empresaId id da empresa
     * @param leilaoId  id do leilao
     */
    @DeleteMapping("/{empresaId}/{leilaoId}")
    public ResponseEntity<Void> excluir(@PathVariable Integer empresaId, @PathVariable Integer leilaoId) {
        autorizacaoBO.exigirProprioOuAdministrador(empresaId);
        compradorBO.excluir(empresaId, leilaoId);
        return ResponseEntity.noContent().build();
    }
}
