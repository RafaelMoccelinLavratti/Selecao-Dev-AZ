package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.UnidadeRequest;
import br.com.selecao.locadora.dto.UnidadeResponse;
import br.com.selecao.locadora.entity.Unidade;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.exception.RegistroNaoEncontradoException;
import br.com.selecao.locadora.repository.UnidadeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 *  CAMADA DE NEGOCIO (business): UNIDADE
 * ============================================================================
 *
 *  ONDE ESTA CAMADA SE ENCAIXA?
 *  O enunciado define a arquitetura em 4 camadas:
 *
 *      service  -> business -> repository -> entity
 *      (HTTP)     (regras)    (dados)      (modelo)
 *
 *  A camada "business" e o CORACAO do sistema: e onde mora o "como" as
 *  regras de negocio sao respeitadas. Ela:
 *    - verifica se o registro existe;
 *    - verifica se a operacaoviola alguma regra (unicidade, integridade);
 *    - normaliza os dados que chegam;
 *    - chama o repositorio para gravar ou ler;
 *    - devolve o resultado ja no formato de resposta (DTO).
 *
 *  REGRA QUE EU SIGO: o controller (camada service) nao deve conter
 *  regra de negocio nenhuma - ele so traduz HTTP <-> Java. Isso deixa
 *  a regra testavel e reusavel.
 * ============================================================================
 */
@Service
public class UnidadeBO {

    /** Log da aplicacao: deixa rastro de quem fez o que e quando. */
    private static final Logger LOG = LoggerFactory.getLogger(UnidadeBO.class);

    /** Acesso ao banco (camada repository). */
    @Autowired
    private UnidadeRepository unidadeRepository;

    /**
     * Lista todas as unidades (GET /unidades).
     */
    public List<UnidadeResponse> buscarTodos() {
        return unidadeRepository.findAll()
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca uma unidade pelo id (GET /unidades/:id).
     *
     * @throws RegistroNaoEncontradoException se o id nao existir
     */
    public UnidadeResponse buscarPorId(Integer id) {
        return paraResponse(buscarEntidade(id));
    }

    /**
     * Cria ou altera uma unidade.
     *
     * O mesmo metodo serve para o POST e para o PUT. Por que?
     * Porque a OPERACAO e a mesma (gravar uma unidade valida); so muda
     * de onde vem o id. Se o id vier nulo, e inclusao; se vier preenchido,
     * e alteracao. Isso evita duplicar codigo (e dois lugares para
     * corrigir um bug depois).
     *
     * @param request dados validados que chegaram da internet
     * @param id      null para inclusao, o id para alteracao
     */
    @Transactional
    public UnidadeResponse salvar(UnidadeRequest request, Integer id) {

        // REGRA DE NEGOCIO 1: o nome da unidade e unico.
        // Verificamos ANTES de gravar para devolver uma mensagem clara,
        // em vez de deixar o banco recusar com uma excecao tecnica.
        if (id == null && unidadeRepository.existsByNome(request.getNome())) {
            throw new RegraNegocioException("Ja existe uma unidade cadastrada com o nome '" + request.getNome() + "'.");
        }
        if (id != null && unidadeRepository.existsByNomeAndIdNot(request.getNome(), id)) {
            throw new RegraNegocioException("Ja existe outra unidade cadastrada com o nome '" + request.getNome() + "'.");
        }

        Unidade unidade = (id == null) ? new Unidade() : buscarEntidade(id);

        // Normalizacao: remove espacos nas pontas. Espaco no inicio/fim de
        // um texto e sempre entrada suja (copia e cola de planilha,
        // digitacao acidental). Gravar limpo evita "Unidade A" e
        // "Unidade A " serem consideredas diferentes.
        unidade.setNome(request.getNome().trim());

        // "saveAndFlush" em vez de "save": o flush (a execucao do SQL) e
        // imediato. Isso importa porque o @PreUpdate - que preenche o
        // campo updated_at - so e disparado no flush. Com "save" puro, o
        // SQL rodaria no fim da transacao e a resposta devolveria o
        // updated_at ANTIGO, enquanto o banco ja teria o novo.
        Unidade salva = unidadeRepository.saveAndFlush(unidade);
        LOG.info("Unidade {} gravada (id={}).", salva.getNome(), salva.getId());
        return paraResponse(salva);
    }

    /**
     * Exclui uma unidade (DELETE /unidades/:id).
     *
     * @throws RegistroNaoEncontradoException se o id nao existir
     */
    @Transactional
    public void excluir(Integer id) {
        Unidade unidade = buscarEntidade(id);
        unidadeRepository.delete(unidade);
        LOG.info("Unidade {} excluida (id={}).", unidade.getNome(), id);
    }

    /**
     * Busca a entidade, devolvendo erro claro se nao existir.
     */
    private Unidade buscarEntidade(Integer id) {
        return unidadeRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Unidade com id " + id + " nao encontrada."));
    }

    /**
     * Converte a entidade do banco no DTO de resposta.
     * Separar as duas coisas evita que mudancas no banco vazeem para a API.
     */
    private UnidadeResponse paraResponse(Unidade unidade) {
        UnidadeResponse response = new UnidadeResponse();
        response.setId(unidade.getId());
        response.setNome(unidade.getNome());
        response.setCreatedAt(unidade.getCreatedAt());
        response.setUpdatedAt(unidade.getUpdatedAt());
        return response;
    }
}
