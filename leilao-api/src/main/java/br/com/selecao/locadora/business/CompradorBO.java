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
 * ============================================================================
 *  CAMADA DE NEGOCIO (business): COMPRADOR
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE ESTA TABELA REPRESENTA
 *  ------------------------------------------------------
 *  O vinculo entre uma EMPRESA e um LEILAO, no papel de compradora. E
 *  uma tabela de relacionamento: ela nao tem dados proprios, apenas
 *  liga duas outras tabelas.
 *
 *  ------------------------------------------------------
 *  POR QUE ESTA TABELA E DIFERENTE DAS OUTRAS?
 *  ------------------------------------------------------
 *  Porque ela nao tem "id" PROPRIO. A chave primaria e o PAR
 *  (empresa, leilao) - uma chave primaria COMPOSTA.
 *
 *  Consequencias praticas em todo o codigo:
 *    - todo metodo recebe DOIS ids, e nao um;
 *    - o "findById" recebe um objeto "CompradorId" (e nao um Integer);
 *    - o mapeamento da entidade usa @EmbeddedId (veja a classe
 *      CompradorId e a anotacao @MapsId nas entidades).
 *
 *  O que a chave composta garante: a mesma empresa nao pode estar duas
 *  vezes no mesmo leilao. Isso nao e uma validacao do negocio - e uma
 *  garantia do proprio banco de dados.
 *
 *  ------------------------------------------------------
 *  SOBRE O PUT (alterar) NESSA TABELA
 *  ------------------------------------------------------
 *  Como a chave primaria e o proprio relacionamento, "alterar" um
 *  comprador significa TROCAR O PAR - por exemplo, a empresa 3 comprou o
 *  leilao 1, e agora o registro passa a dizer "empresa 3 no leilao 2".
 *
 *  Tecnicamente isso e apagar + inserir. E e isso que o codigo faz,
 *  dentro de uma transacao: ou os dois passos acontecem, ou nenhum.
 *  Sem a transacao, um erro no meio deixaria o vinculo antigo apagado
 *  e o novo nao criado - perda de dados sem possibilidade de recuperacao.
 * ============================================================================
 */
@Service
public class CompradorBO {

    /**
     * Logger da camada de negocio.
     *
     * O SLF4J e a fachada de logging usada no Java. O padrao e
     * "private static final", porque:
     *   - "static": o logger pertence a CLASSE, e nao a cada objeto
     *     (evita criar um por instancia);
     *   - "final": a referencia nao muda depois de criada.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CompradorBO.class);

    /** Repositorio do vinculo comprador. */
    @Autowired
    private CompradorRepository compradorRepository;

    /** Repositorio das empresas (para validar e para enriquecer a resposta). */
    @Autowired
    private EmpresaRepository empresaRepository;

    /** Repositorio dos leiloes (idem). */
    @Autowired
    private LeilaoRepository leilaoRepository;

    /**
     * Lista todos os vinculos empresa-leilao.
     *
     * O padrao "stream" traduz o SQL:
     *      findAll()   -> busca todos
     *      .stream()   -> percorrer
     *      .map(...)   -> transformar cada um
     *      .collect()  -> juntar numa lista
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
     * GlobalExceptionHandler ja sabe transformar essa excecao em um
     * HTTP 404 padronizado.
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
     * @Transactional garante que a gravacao seja ATOMICA: ou o vinculo e
     * criado inteiro, ou nada e criado. Tambem garante que a regra de
     * negocio (validar empresa, validar leilao, checar duplicidade) e
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
     * @param empresaId id da empresa do vinculo ATUAL
     * @param leilaoId  id do leilao do vinculo ATUAL
     * @param request   empresa e leilao do vinculo NOVO
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

        // Trocar a chave primaria = apagar o vinculo antigo e criar o novo.
        // A anotacao @Transactional garante que os dois passos sejam um so:
        // se o segundo falhar, o primeiro e desfeito (rollback).
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
     * E "privado" porque nao faz sentido chamar de fora: ele sempre
     * cria, e a criacao e publica por "salvar" (e usada internamente
     * por "atualizar"). Manter o metodo privado impede que alguem
     * insira um vinculo sem passar pela validacao.
     *
     * @param id chave composta (empresa + leilao)
     * @return o vinculo criado
     */
    private CompradorResponse criar(CompradorId id) {

        // REGRA 1: a empresa precisa existir.
        Empresa empresa = empresaRepository.findById(id.getEmpresa())
                .orElseThrow(() -> new RegraNegocioException(
                        "A empresa de id " + id.getEmpresa() + " nao existe."));

        // REGRA 2: o leilao precisa existir.
        Leilao leilao = leilaoRepository.findById(id.getLeilao())
                .orElseThrow(() -> new RegraNegocioException(
                        "O leilao de id " + id.getLeilao() + " nao existe."));

        // REGRA 3: o mesmo par nao pode se repetir (e o que a PK composta
        // garantiria no banco, mas validamos aqui para dar boa mensagem).
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

        // Busca os nomes para a tela. Se o registro apontar para algo que
        // foi apagado (nao deveria acontecer por causa das FKs), devolvemos
        // null em vez de estourar uma excecao.
        empresaRepository.findById(id.getEmpresa())
                .ifPresent(e -> response.setRazaoSocial(e.getRazaoSocial()));
        leilaoRepository.findById(id.getLeilao())
                .ifPresent(l -> response.setCodigoLeilao(l.getCodigo()));

        return response;
    }
}
