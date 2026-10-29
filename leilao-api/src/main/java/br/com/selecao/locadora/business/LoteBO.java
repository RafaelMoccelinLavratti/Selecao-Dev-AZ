package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.LoteRequest;
import br.com.selecao.locadora.dto.LoteResponse;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.entity.Lote;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.exception.RegistroNaoEncontradoException;
import br.com.selecao.locadora.repository.LeilaoRepository;
import br.com.selecao.locadora.repository.LoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 *  CAMADA DE NEGOCIO (business): LOTE
 * ============================================================================
 *
 *  REGRA PRINCIPAL DESTA CLASSE: o total do lote.
 *
 *  total do lote = quantidade * valorInicial
 *
 *  E COMO O TOTAL DO LEILAO E MONTADO (exercicio 7)?
 *  Somando o total de todos os lotes daquele leilao:
 *      total do leilao = SUM(lote.quantidade * lote.valorInicial)
 *
 *  O calculo usa BigDecimal (dinheiro exato), nunca double.
 * ============================================================================
 */
@Service
public class LoteBO {

    private static final Logger LOG = LoggerFactory.getLogger(LoteBO.class);

    @Autowired
    private LoteRepository loteRepository;

    @Autowired
    private LeilaoRepository leilaoRepository;

    /**
     * Lista todos os lotes.
     */
    public List<LoteResponse> buscarTodos() {
        return loteRepository.findAll()
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista os lotes de um leilao especifico.
     */
    public List<LoteResponse> buscarPorLeilao(Integer leilaoId) {
        return loteRepository.findByLeilaoId(leilaoId)
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca um lote pelo id.
     */
    public LoteResponse buscarPorId(Integer id) {
        return paraResponse(buscarEntidade(id));
    }

    /**
     * Cria ou altera um lote.
     */
    @Transactional
    public LoteResponse salvar(LoteRequest request, Integer id) {

        // REGRA: o leilao do lote precisa existir.
        Leilao leilao = leilaoRepository.findById(request.getLeilao())
                .orElseThrow(() -> new RegraNegocioException(
                        "O leilao de id " + request.getLeilao() + " nao existe."));

        Lote lote = (id == null) ? new Lote() : buscarEntidade(id);

        lote.setNumeroLote(request.getNumeroLote());
        lote.setDescricao(request.getDescricao());
        lote.setQuantidade(request.getQuantidade());
        lote.setValorInicial(request.getValorInicial());
        lote.setUnidade(request.getUnidade());
        lote.setLeilao(leilao);

        // "saveAndFlush": garante que o @PreUpdate ja tenha preenchido o
        // updated_at antes de montarmos a resposta.
        Lote salvo = loteRepository.saveAndFlush(lote);
        LOG.info("Lote {} gravado (id={}).", salvo.getNumeroLote(), salvo.getId());
        return paraResponse(salvo);
    }

    /**
     * Exclui um lote.
     */
    @Transactional
    public void excluir(Integer id) {
        Lote lote = buscarEntidade(id);
        loteRepository.delete(lote);
        LOG.info("Lote {} excluido (id={}).", lote.getNumeroLote(), id);
    }

    /**
     * Busca a entidade, devolvendo erro claro se nao existir.
     */
    private Lote buscarEntidade(Integer id) {
        return loteRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Lote com id " + id + " nao encontrado."));
    }

    /**
     * Converte a entidade no DTO de resposta, ja com o total do lote.
     */
    private LoteResponse paraResponse(Lote lote) {
        return LoteResponse.builder()
                .id(lote.getId())
                .numeroLote(lote.getNumeroLote())
                .descricao(lote.getDescricao())
                .quantidade(lote.getQuantidade())
                .valorInicial(lote.getValorInicial())
                .totalLote(calcularTotalLote(lote.getQuantidade(), lote.getValorInicial()))
                .unidade(lote.getUnidade())
                .leilao(lote.getLeilao() != null ? lote.getLeilao().getId() : null)
                .createdAt(lote.getCreatedAt())
                .updatedAt(lote.getUpdatedAt())
                .build();
    }

    /**
     * Calcula quantidade * valorInicial com dinheiro exato.
     *
     * Se algum dos dois for nulo, o total e zero (e nao um erro): a tela
     * precisa de um numero para exibir, e a coluna do banco permite nulo.
     */
    private BigDecimal calcularTotalLote(Integer quantidade, BigDecimal valorInicial) {
        if (quantidade == null || valorInicial == null) {
            return BigDecimal.ZERO;
        }
        // multiply com BigDecimal: 5 * 12500.00 = 62500.00 exato.
        return valorInicial.multiply(BigDecimal.valueOf(quantidade));
    }
}
