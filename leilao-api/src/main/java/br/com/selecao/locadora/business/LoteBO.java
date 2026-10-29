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
 *  ------------------------------------------------------
 *  O QUE E UM LOTE?
 *  ------------------------------------------------------
 *  O lote e o item que esta sendo leiloado - e ele que carrega o
 *  QUANTIDADE e o VALOR. Um leilao tem varios lotes; o valor total do
 *  leilao e a soma dos valores dos lotes.
 *
 *  ------------------------------------------------------
 *  A REGRA PRINCIPAL DESTA CLASSE: O TOTAL DO LOTE
 *  ------------------------------------------------------
 *      total do lote = quantidade * valorInicial
 *
 *  E COMO O TOTAL DO LEILAO E MONTADO (exercicio 7)?
 *  Somando o total de todos os lotes daquele leilao:
 *      total do leilao = SUM(lote.quantidade * lote.valorInicial)
 *
 *  Repare que o total do LEILAO e calculado NO BANCO (veja a JPQL em
 *  LeilaoRepository), enquanto o total do LOTE e calculado AQUI, em
 *  Java. Nao e inconsistencia: sao granularidades diferentes. O total
 *  do lote e o valor de UM item (para a tela mostrar na tabela de
 *  lotes); o total do leilao agrega VARIOS lotes, e fazer essa soma
 *  direto no banco e mais eficiente (um unico SELECT em vez de N
 *  consultas).
 *
 *  ------------------------------------------------------
 *  POR QUE BIGDECIMAL E NUNCA DOUBLE?
 *  ------------------------------------------------------
 *  Este e o ponto mais importante desta classe do ponto de vista de
 *  CORRECAO FINANCEIRA.
 *
 *  O tipo "double" (e o "float") armazena numeros em base 2 (binario).
 *  Como 0,1 nao tem representacao exata em binario, o computador
 *  guarda um valor MUITO PROXIMO - e o famoso:
 *
 *      System.out.println(0.1 + 0.2);   // 0.30000000000000004
 *
 *  Isso parece irrelevante, mas em dinheiro nao e. Compare:
 *
 *      double:      0.1 + 0.2 = 0.30000000000000004  (erro)
 *      BigDecimal:  0.1 + 0.2 = 0.30                (exato)
 *
 *  Ao somar hundreds de lotes com valores fracionarios, o erro se
 *  acumula - e o total exibido na tela deixa de bater com o total
 *  real. Em um sistema de leilao, isso nao e "imprecisao aceitavel":
 *  e dinheiro a mais ou a menos.
 *
 *  O BigDecimal resolve isso porque representa numeros DECIMAIS de
 *  forma exata (como um numero escrito a mao). E ele traz um detalhe
 *  importante: NAO se usa o operador "*" com ele. Para multiplicar,
 *  usa-se o metodo ".multiply()", que exige que os dois lados
 *  tambem sejam BigDecimal. Isso e proposital - se um dos lados for
 *  um double, o Java impede a operacao, forcando a conversao
 *  explicita e impedindo que o erro entre sorrateiramente.
 *
 *  ------------------------------------------------------
 *  E O "BigDecimal.valueOf"?  (e nao "new BigDecimal")
 *  ------------------------------------------------------
 *  BigDecimal.valueOf(5) cria o numero 5 exato.
 *  new BigDecimal(0.1) cria o valor mais proximo de 0.1 em double -
 *  ou seja, o MESMO erro do double, so que escondido dentro de um
 *  BigDecimal. Por isso a regra e: SEMPRE use valueOf.
 * ============================================================================
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

    /** Repositorio dos leiloes (para validar o leilao do lote). */
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
     * metodo "findByLeilaoId" na interface do repositorio gera
     * automaticamente o SQL equivalente. Nao ha SQL escrito a mao.
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
     * O mesmo metodo serve para os dois casos: se o "id" vier nulo, e
     * uma inclusao; se vier preenchido, e uma alteracao. Esse padrao
     * (um metodo para criar e alterar) e comum em projetos pequeno e
     * evita duplicar a logica de validacao.
     *
     * @param request dados do lote
     * @param id      id do lote a alterar (null = inclusao)
     * @return o lote gravado
     */
    @Transactional
    public LoteResponse salvar(LoteRequest request, Integer id) {

        // REGRA: o leilao do lote precisa existir.
        // Sem esta validacao, o usuario poderia cadastrar um lote
        // apontando para um leilao inexistente (o banco recusaria com
        // uma foreign key, mas com uma mensagem tecnica e feia).
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

        // "saveAndFlush": o "save" normal pode apenas agendar a gravacao
        // para o fim da transacao. O "flush" forca a execucao do SQL
        // AGORA - e assim garantimos que o @PreUpdate (que preenche o
        // campo "updated_at") ja tenha rodado antes de montarmos a
        // resposta, para ela nao vir com a data de alteracao vazia.
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
     * O metodo "paraResponse" e a fronteira da camada: e aqui que a
     * ENTIDADE (formato do banco) vira DTO (formato da API). Ele
     * garante que nada sensivel da entidade vaze para a resposta -
     * no caso da empresa, por exemplo, o campo "senha" simplesmente nao
     * existe no DTO de saida.
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
        // O "BigDecimal.valueOf(quantidade)" converte o Integer para
        // BigDecimal - e o que permite usar o metodo "multiply".
        return valorInicial.multiply(BigDecimal.valueOf(quantidade));
    }
}
