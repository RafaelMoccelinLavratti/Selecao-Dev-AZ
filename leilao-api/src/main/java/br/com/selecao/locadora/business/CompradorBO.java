package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.CompradorRequest;
import br.com.selecao.locadora.dto.CompradorResponse;
import br.com.selecao.locadora.entity.Comprador;
import br.com.selecao.locadora.entity.CompradorId;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.exception.RegistroNaoEncontradoException;
import br.com.selecao.locadora.repository.CompradorRepository;
import br.com.selecao.locadora.repository.EmpresaRepository;
import br.com.selecao.locadora.repository.LeilaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Regras de negocio do vinculo entre empresa e leilao.
 *
 * A tabela comprador e uma juncao: liga uma empresa a um leilao no papel
 * de compradora. A chave primaria e o proprio par (empresa, leilao), o que
 * torna o vinculo unico por definicao.
 */
@Service
public class CompradorBO {

    /**
     * Logger da camada de negocio.
     *
     * O SLF4J e a fachada de logging usada no Java. O padrao e
     * "private static final" porque o logger pertence a classe (e nao a
     * cada objeto) e a referencia nao muda depois de criada.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CompradorBO.class);

    /** Repositorio do vinculo comprador. */
    @Autowired
    private CompradorRepository compradorRepository;

    /** Repositorio das empresas (pra validar e pra enriquecer a resposta). */
    @Autowired
    private EmpresaRepository empresaRepository;

    /** Repositorio dos leiloes (idem). */
    @Autowired
    private LeilaoRepository leilaoRepository;

    /**
     * Lista todos os vinculos empresa-leilao.
     *
     * O padrao "stream" traduz o SQL: findAll() busca todos, .stream()
     * percorre, .map() transforma cada um e .collect() junta numa lista.
     *
     * @return lista de todos os vinculos
     */
    public List<CompradorResponse> buscarTodos() {
        return compradorRepository.findAll()
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca um vinculo especifico pela chave composta.
     *
     * O "orElseThrow" e o "optional" do Java 8+: quando o repositorio
     * devolve um Optional vazio (nao encontrou), lancamos a excecao de
     * negocio em vez de devolver null. Devolver null espalharia a
     * verificacao de "existe?" por toda a aplicacao - e o
     * GlobalExceptionHandler ja sabe transformar essa excecao num HTTP 404
     * padronizado.
     *
     * @param empresaId id da empresa compradora
     * @param leilaoId  id do leilao
     * @return o vinculo encontrado
     */
    public CompradorResponse buscarPorId(Integer empresaId, Integer leilaoId) {
        Comprador comprador = compradorRepository.findById(new CompradorId(empresaId, leilaoId))
                .orElseThrow(() -> new RegistroNaoEncontradoException(
                        "A empresa " + empresaId + " nao consta como compradora do leilao " + leilaoId + "."));
        return paraResponse(comprador);
    }

    /**
     * Registra a participacao de uma empresa em um leilao como compradora.
     *
     * @Transactional garante que a gravacao seja atomica: ou o vinculo e
     * criado inteiro, ou nada e criado. Tambem garante que a regra de
     * negocio (validar empresa, validar leilao, checar duplicidade) seja
     * avaliada com o banco consistente.
     *
     * @param request empresa e leilao do vinculo
     * @return o vinculo criado, com os nomes legiveis
     */
    @Transactional
    public CompradorResponse salvar(CompradorRequest request) {
        return criar(new CompradorId(request.getEmpresa(), request.getLeilao()));
    }

    /**
     * Altera o vinculo existente (troca o par empresa/leilao).
     *
     * @param empresaId id da empresa do vinculo atual
     * @param leilaoId  id do leilao do vinculo atual
     * @param request   empresa e leilao do vinculo novo
     * @return o vinculo novo
     */
    @Transactional
    public CompradorResponse atualizar(Integer empresaId, Integer leilaoId, CompradorRequest request) {

        // Garante que o vinculo antigo existe.
        compradorRepository.findById(new CompradorId(empresaId, leilaoId))
                .orElseThrow(() -> new RegistroNaoEncontradoException(
                        "A empresa " + empresaId + " nao consta como compradora do leilao " + leilaoId + "."));

        // Se o par nao mudou, nao ha nada a fazer.
        CompradorId novoId = new CompradorId(request.getEmpresa(), request.getLeilao());
        if (novoId.equals(new CompradorId(empresaId, leilaoId))) {
            return buscarPorId(empresaId, leilaoId);
        }

        // Trocar a chave primaria e apagar o vinculo antigo e criar o novo.
        // O @Transactional garante que os dois passos sejam um so: se o
        // segundo falhar, o primeiro e desfeito (rollback).
        compradorRepository.deleteById(new CompradorId(empresaId, leilaoId));
        return criar(novoId);
    }

    /**
     * Remove o vinculo.
     *
     * @param empresaId id da empresa compradora
     * @param leilaoId  id do leilao
     */
    @Transactional
    public void excluir(Integer empresaId, Integer leilaoId) {
        compradorRepository.findById(new CompradorId(empresaId, leilaoId))
                .orElseThrow(() -> new RegistroNaoEncontradoException(
                        "A empresa " + empresaId + " nao consta como compradora do leilao " + leilaoId + "."));
        compradorRepository.deleteById(new CompradorId(empresaId, leilaoId));
        LOG.info("Vinculo comprador (empresa={}, leilao={}) removido.", empresaId, leilaoId);
    }

    /**
     * Metodo auxiliar que grava um vinculo novo, validando as regras.
     *
     * E privado porque nao faz sentido chamar de fora: ele sempre cria, e
     * a criacao e publica por "salvar" (e usada internamente por
     * "atualizar"). Manter o metodo privado impede que alguem insira um
     * vinculo sem passar pela validacao.
     *
     * @param id chave composta (empresa + leilao)
     * @return o vinculo criado
     */
    private CompradorResponse criar(CompradorId id) {

        // A empresa precisa existir.
        Empresa empresa = empresaRepository.findById(id.getEmpresa())
                .orElseThrow(() -> new RegraNegocioException(
                        "A empresa de id " + id.getEmpresa() + " nao existe."));

        // O leilao precisa existir.
        Leilao leilao = leilaoRepository.findById(id.getLeilao())
                .orElseThrow(() -> new RegraNegocioException(
                        "O leilao de id " + id.getLeilao() + " nao existe."));

        // O mesmo par nao pode se repetir. E o que a PK composta garantiria
        // no banco, mas validamos aqui pra dar uma boa mensagem.
        if (compradorRepository.existsByIdEmpresaAndIdLeilao(id.getEmpresa(), id.getLeilao())) {
            throw new RegraNegocioException(
                    "A empresa " + id.getEmpresa() + " ja consta como compradora do leilao " + id.getLeilao() + ".");
        }

        Comprador comprador = new Comprador();
        comprador.setId(id);
        Comprador salvo = compradorRepository.saveAndFlush(comprador);
        LOG.info("Vinculo comprador criado (empresa={}, leilao={}).", id.getEmpresa(), id.getLeilao());

        // Devolve a resposta enrichida com os dados legiveis.
        CompradorResponse response = paraResponse(salvo);
        response.setRazaoSocial(empresa.getRazaoSocial());
        response.setCodigoLeilao(leilao.getCodigo());
        return response;
    }

    /**
     * Converte a entidade no DTO de resposta.
     */
    private CompradorResponse paraResponse(Comprador comprador) {
        CompradorId id = comprador.getId();
        CompradorResponse response = CompradorResponse.builder()
                .empresa(id.getEmpresa())
                .leilao(id.getLeilao())
                .createdAt(comprador.getCreatedAt())
                .updatedAt(comprador.getUpdatedAt())
                .build();

        // Busca os nomes pra tela. Se o registro apontar pra algo que foi
        // apagado (nao deveria acontecer por causa das FKs), devolvemos
        // null em vez de estourar uma excecao.
        empresaRepository.findById(id.getEmpresa())
                .ifPresent(e -> response.setRazaoSocial(e.getRazaoSocial()));
        leilaoRepository.findById(id.getLeilao())
                .ifPresent(l -> response.setCodigoLeilao(l.getCodigo()));

        return response;
    }
}
