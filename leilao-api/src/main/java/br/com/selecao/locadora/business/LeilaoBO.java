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
 * vendedor e total de cada leilao. E aqui tambem que se confere se o
 * vendedor existe, pra devolver mensagem em portugues em vez de erro de
 * banco.
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
     * Lista os leiloes que uma empresa pode ver, ja com o total.
     *
     * A consulta escopada vem do banco: a empresa ve os leiloes que
     * vende e os leiloes em que esta inscrita como compradora.
     *
     * @param empresaId id da empresa
     * @return os leiloes visiveis
     */
    public List<LeilaoResumoDTO> buscarResumoParaEmpresa(Integer empresaId) {
        return leilaoRepository.findResumoVisivelParaEmpresa(empresaId);
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
     *
     * O "readOnly = true" e' obrigatorio, e nao cosmetico.
     *
     * O "vendedor" e' uma relacao "LAZY": o Hibernate devolve um
     * proxy, e o proxy so' busca os dados de verdade quando o codigo
     * chama um metodo dele - como o "getRazaoSocial()" do
     * "paraResponse". Essa busca acontece porem na sessao do banco, e a
     * sessao fecha quando o metodo devolve.
     *
     * Como o "open-in-view" esta' desligado (e' a configuracao
     * correta - segurar a sessao durante a serializacao e' o que causa
     * o N+1), o proxy perde a sessao e explode com
     * "LazyInitializationException" - que o frontend recebia como HTTP
     * 500.
     *
     * O "@Transactional(readOnly = true)" mantem a sessao aberta
     * durante o mapeamento, sem abrir transacao de escrita.
     *
     * A lista ("buscarResumo") nao tinha esse problema porque a
     * consulta JPQL projeta o "razaoSocial" direto no banco.
     */
    @Transactional(readOnly = true)
    public LeilaoResponse buscarPorId(Integer id) {
        return paraResponse(buscarEntidade(id));
    }

    /**
     * Cria ou altera um leilao.
     */
    @Transactional
    public LeilaoResponse salvar(LeilaoRequest request, Integer id) {

        // O vendedor precisa existir.
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
                .orElseThrow(() -> new RegistroNaoEncontradoException("Leilão com id " + id + " não encontrado."));
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
