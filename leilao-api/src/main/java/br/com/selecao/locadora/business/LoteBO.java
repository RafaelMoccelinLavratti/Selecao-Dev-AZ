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
 * Regras de negocio do lote.
 *
 * O total do lote e quantidade x valor inicial, e o total do leilao e a soma
 * dos totais dos lotes. Valores monetarios usam BigDecimal: em double,
 * 0,1 + 0,2 nao da 0,3, e a soma de centavos errada no fim do leilao e um
 * problema real.
 */
@Service
public class LoteBO {

    /**
     * Logger da camada de negocio.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LoteBO.class);

    /** Repositorio dos lotes. */
    @Autowired
    private LoteRepository loteRepository;

    /** Repositorio dos leiloes (pra validar o leilao do lote). */
    @Autowired
    private LeilaoRepository leilaoRepository;

    /**
     * Lista todos os lotes.
     *
     * @return lista de todos os lotes, ja com o total calculado
     */
    public List<LoteResponse> buscarTodos() {
        return loteRepository.findAll()
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista os lotes de um leilao especifico.
     *
     * Este e o metodo derivado do nome do metodo no Spring Data: o
     * "findByLeilaoId" na interface do repositorio gera automaticamente
     * o SQL equivalente. Nao ha SQL escrito a mao.
     *
     * @param leilaoId id do leilao
     * @return os lotes que pertencem a esse leilao
     */
    public List<LoteResponse> buscarPorLeilao(Integer leilaoId) {
        return loteRepository.findByLeilaoId(leilaoId)
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca um lote pelo id.
     *
     * @param id id do lote
     * @return o lote encontrado
     */
    public LoteResponse buscarPorId(Integer id) {
        return paraResponse(buscarEntidade(id));
    }

    /**
     * Cria ou altera um lote.
     *
     * O mesmo metodo serve pros dois casos: se o "id" vier nulo, e uma
     * inclusao; se vier preenchido, e uma alteracao. Esse padrao (um
     * metodo pra criar e alterar) e comum em projeto pequeno e evita
     * duplicar a logica de validacao.
     *
     * @param request dados do lote
     * @param id      id do lote a alterar (null = inclusao)
     * @return o lote gravado
     */
    @Transactional
    public LoteResponse salvar(LoteRequest request, Integer id) {

        // O leilao do lote precisa existir. Sem esta validacao, o usuario
        // poderia cadastrar um lote apontando pra um leilao inexistente
        // (o banco recusaria com uma foreign key, mas com uma mensagem
        // tecnica e feia).
        Leilao leilao = leilaoRepository.findById(request.getLeilao())
                .orElseThrow(() -> new RegraNegocioException(
                        "O leilao de id " + request.getLeilao() + " nao existe."));

        // Se o id veio nulo, e uma inclusao (nova entidade). Se veio
        // preenchido, e uma alteracao (busca a existente).
        Lote lote = (id == null) ? new Lote() : buscarEntidade(id);

        // Preenche os campos com os dados que chegaram no DTO.
        lote.setNumeroLote(request.getNumeroLote());
        lote.setDescricao(request.getDescricao());
        lote.setQuantidade(request.getQuantidade());
        lote.setValorInicial(request.getValorInicial());
        lote.setUnidade(request.getUnidade());
        lote.setLeilao(leilao);

        // saveAndFlush: o "save" normal pode apenas agendar a gravacao
        // pro fim da transacao. O "flush" forca a execucao do SQL agora -
        // e assim o @PreUpdate (que preenche o campo "updated_at") ja
        // rodou antes de montarmos a resposta, pra ela nao voltar com a
        // data de alteracao vazia.
        Lote salvo = loteRepository.saveAndFlush(lote);
        LOG.info("Lote {} gravado (id={}).", salvo.getNumeroLote(), salvo.getId());
        return paraResponse(salvo);
    }

    /**
     * Exclui um lote.
     *
     * @param id id do lote a excluir
     */
    @Transactional
    public void excluir(Integer id) {
        Lote lote = buscarEntidade(id);
        loteRepository.delete(lote);
        LOG.info("Lote {} excluido (id={}).", lote.getNumeroLote(), id);
    }

    /**
     * Busca a entidade, devolvendo erro claro se nao existir.
     *
     * Este metodo privado evita repetir o mesmo "findById + orElseThrow"
     * em todos os metodos publicos.
     *
     * @param id id do lote
     * @return a entidade do lote
     */
    private Lote buscarEntidade(Integer id) {
        return loteRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Lote com id " + id + " nao encontrado."));
    }

    /**
     * Converte a entidade no DTO de resposta, ja com o total do lote.
     *
     * O "paraResponse" e a fronteira da camada: e aqui que a entidade
     * (formato do banco) vira DTO (formato da API). Ele garante que nada
     * sensivel da entidade vaze pra resposta - no caso da empresa, por
     * exemplo, o campo "senha" simplesmente nao existe no DTO de saida.
     *
     * @param lote entidade do lote
     * @return o DTO correspondente
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
     *
     * @param quantidade   quantidade de itens do lote
     * @param valorInicial valor unitario de cada item
     * @return o total do lote
     */
    private BigDecimal calcularTotalLote(Integer quantidade, BigDecimal valorInicial) {
        if (quantidade == null || valorInicial == null) {
            return BigDecimal.ZERO;
        }
        // multiply com BigDecimal: 5 * 12500.00 = 62500.00 exato.
        // O "BigDecimal.valueOf(quantidade)" converte o Integer pra
        // BigDecimal - e o que permite usar o metodo "multiply".
        return valorInicial.multiply(BigDecimal.valueOf(quantidade));
    }
}
