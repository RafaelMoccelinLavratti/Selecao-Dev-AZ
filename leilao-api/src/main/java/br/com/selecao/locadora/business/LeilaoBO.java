package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.LeilaoRequest;
import br.com.selecao.locadora.dto.LeilaoResponse;
import br.com.selecao.locadora.dto.LeilaoResumoDTO;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.exception.RegistroNaoEncontradoException;
import br.com.selecao.locadora.repository.EmpresaRepository;
import br.com.selecao.locadora.repository.LeilaoRepository;
import br.com.selecao.locadora.repository.LoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Regras de negocio do leilao.
 *
 * Alem do CRUD, e aqui que mora a listagem resumida (exercicio 7), que traz
 * vendedor e total de cada leilao, e a regra de que o vendedor precisa
 * existir - validada aqui para devolver mensagem em portugues em vez de
 * erro de banco.
 */
@Service
public class LeilaoBO {

    private static final Logger LOG = LoggerFactory.getLogger(LeilaoBO.class);

    @Autowired
    private LeilaoRepository leilaoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private LoteRepository loteRepository;

    /**
     * Lista os leiloes JA COM o total calculado e a razao social do
     * vendedor - que e exatamente o que a tela do exercicio 7 mostra.
     */
    public List<LeilaoResumoDTO> buscarResumo() {
        return leilaoRepository.findResumoLeiloes();
    }

    /**
     * Lista os leiloes em formato simples (sem o total).
     */
    public List<LeilaoResponse> buscarTodos() {
        return leilaoRepository.findAll()
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca um leilao pelo id.
     */
    public LeilaoResponse buscarPorId(Integer id) {
        return paraResponse(buscarEntidade(id));
    }

    /**
     * Cria ou altera um leilao.
     */
    @Transactional
    public LeilaoResponse salvar(LeilaoRequest request, Integer id) {

        // REGRA: o vendedor precisa existir.
        Empresa vendedor = empresaRepository.findById(request.getVendedor())
                .orElseThrow(() -> new RegraNegocioException(
                        "A empresa vendedora de id " + request.getVendedor() + " nao existe."));

        Leilao leilao = (id == null) ? new Leilao() : buscarEntidade(id);

        leilao.setCodigo(request.getCodigo());
        leilao.setDescricao(request.getDescricao());
        leilao.setVendedor(vendedor);
        leilao.setInicioPrevisto(request.getInicioPrevisto());

        // "saveAndFlush": garante que o @PreUpdate ja tenha preenchido o
        // updated_at antes de montarmos a resposta.
        Leilao salvo = leilaoRepository.saveAndFlush(leilao);
        LOG.info("Leilao {} gravado (id={}).", salvo.getCodigo(), salvo.getId());
        return paraResponse(salvo);
    }

    /**
     * Exclui um leilao.
     *
     * Se ainda houver lotes, avisamos o usuario em vez de apagar tudo
     * junto em cascata (o historico de precos sumiria silenciosamente).
     */
    @Transactional
    public void excluir(Integer id) {
        Leilao leilao = buscarEntidade(id);

        if (loteRepository.existsByLeilaoId(id)) {
            throw new RegraNegocioException(
                    "Este leilao nao pode ser excluido porque ainda possui lotes vinculados.");
        }

        leilaoRepository.delete(leilao);
        LOG.info("Leilao {} excluido (id={}).", leilao.getCodigo(), id);
    }

    /**
     * Busca a entidade, devolvendo erro claro se nao existir.
     */
    private Leilao buscarEntidade(Integer id) {
        return leilaoRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Leilao com id " + id + " nao encontrado."));
    }

    /**
     * Converte a entidade no DTO de resposta.
     */
    private LeilaoResponse paraResponse(Leilao leilao) {
        return LeilaoResponse.builder()
                .id(leilao.getId())
                .codigo(leilao.getCodigo())
                .descricao(leilao.getDescricao())
                .vendedor(leilao.getVendedor() != null ? leilao.getVendedor().getId() : null)
                .razaoSocial(leilao.getVendedor() != null ? leilao.getVendedor().getRazaoSocial() : null)
                .inicioPrevisto(leilao.getInicioPrevisto())
                .createdAt(leilao.getCreatedAt())
                .updatedAt(leilao.getUpdatedAt())
                .build();
    }
}
