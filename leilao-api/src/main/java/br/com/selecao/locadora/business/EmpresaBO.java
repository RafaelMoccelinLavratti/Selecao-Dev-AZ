package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.EmpresaRequest;
import br.com.selecao.locadora.dto.EmpresaResponse;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.exception.RegistroNaoEncontradoException;
import br.com.selecao.locadora.repository.EmpresaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Regras de negocio da empresa.
 *
 * Tres responsabilidades: normalizar a entrada (CNPJ, CEP e telefone ficam
 * gravados so com digitos), recusar CNPJ e usuario repetidos com mensagem
 * clara, e guardar a senha como hash em vez de texto puro.
 */
@Service
public class EmpresaBO {

    private static final Logger LOG = LoggerFactory.getLogger(EmpresaBO.class);

    @Autowired
    private EmpresaRepository empresaRepository;

    /** Codificador BCrypt (injetado pelo Spring - ver config/PasswordEncoderConfig). */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Lista todas as empresas (GET /empresas).
     */
    public List<EmpresaResponse> buscarTodos() {
        return empresaRepository.findAll()
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca uma empresa pelo id (GET /empresas/:id).
     */
    public EmpresaResponse buscarPorId(Integer id) {
        return paraResponse(buscarEntidade(id));
    }

    /**
     * Cria ou altera uma empresa.
     *
     * @param request dados validados que chegaram da internet
     * @param id      null para inclusao, o id para alteracao
     */
    @Transactional
    public EmpresaResponse salvar(EmpresaRequest request, Integer id) {

        // --- PASSO 1: NORMALIZAR (tirar pontuacao e espacos) ---
        String cnpj = somenteDigitos(request.getCnpj());
        String cep = request.getCep() == null ? null : somenteDigitos(request.getCep());
        String telefone = request.getTelefone() == null ? null : somenteDigitos(request.getTelefone());
        String usuario = request.getUsuario() == null ? null : request.getUsuario().trim();

        // Passo 2: verificar unicidade
        // "id == null" -> we're incluindo (checa se ja existe algum).
        // "id != null" -> estamos alterando (o proprio registro nao conta).
        if (id == null) {
            if (empresaRepository.existsByCnpj(cnpj)) {
                throw new RegraNegocioException("Ja existe uma empresa cadastrada com o CNPJ " + cnpj + ".");
            }
        } else {
            if (empresaRepository.existsByCnpjAndIdNot(cnpj, id)) {
                throw new RegraNegocioException("Ja existe outra empresa cadastrada com o CNPJ " + cnpj + ".");
            }
        }

        if (usuario != null && !usuario.isEmpty()) {
            if (id == null) {
                if (empresaRepository.existsByUsuario(usuario)) {
                    throw new RegraNegocioException("Ja existe uma empresa cadastrada com o usuario '" + usuario + "'.");
                }
            } else {
                if (empresaRepository.existsByUsuarioAndIdNot(usuario, id)) {
                    throw new RegraNegocioException("Ja existe outra empresa cadastrada com o usuario '" + usuario + "'.");
                }
            }
        }

        // Passo 3: montar a entidade
        Empresa empresa = (id == null) ? new Empresa() : buscarEntidade(id);

        empresa.setRazaoSocial(request.getRazaoSocial().trim());
        empresa.setCnpj(cnpj);
        empresa.setLogadouro(request.getLogadouro());
        empresa.setMunicipio(request.getMunicipio());
        empresa.setNumero(request.getNumero());
        empresa.setComplemento(request.getComplemento());
        empresa.setQuantidade(request.getQuantidade());
        empresa.setCep(cep);
        empresa.setTelefone(telefone);
        empresa.setEmail(request.getEmail());
        empresa.setSite(request.getSite());
        empresa.setUsuario(usuario);

        // Passo 4: tratar a senha
        // Se o cliente mandou senha, codificamos com BCrypt.
        // Se NAO mandou (alteracao sem mexer na senha), mantemos o hash
        // que ja estava gravado - assim uma edicao de e-mail nao apaga
        // a senha do usuario.
        if (request.getSenha() != null && !request.getSenha().isBlank()) {
            empresa.setSenha(passwordEncoder.encode(request.getSenha()));
        } else if (id == null) {
            // Inclusao sem senha: deixa vazio (o diagrama torna o campo opcional).
            empresa.setSenha(null);
        }

        // Passo 5: gravar
        // "saveAndFlush": o flush e imediato, entao o @PreUpdate ja
        // preencheu o updated_at e a resposta traz o valor correto.
        // (Com "save" puro, o SQL rodaria no fim da transacao e a
        //  resposta mostraria o updated_at antigo.)
        Empresa salva = empresaRepository.saveAndFlush(empresa);
        LOG.info("Empresa {} gravada (id={}).", salva.getRazaoSocial(), salva.getId());
        return paraResponse(salva);
    }

    /**
     * Exclui uma empresa (DELETE /empresas/:id).
     *
     * REGRA: o banco tem ON DELETE RESTRICT em tb_leilao.vendedor, ou
     * seja, uma empresa que vendeu leiloes nao pode ser apagada. O
     * banco protege a integridade; nos interceptamos essa recusa para
     * devolver uma mensagem clara em vez de um erro tecnico.
     */
    @Transactional
    public void excluir(Integer id) {
        Empresa empresa = buscarEntidade(id);

        try {
            empresaRepository.delete(empresa);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new RegraNegocioException(
                    "Esta empresa nao pode ser excluida porque possui leiloes ou participacoes vinculadas.");
        }
        LOG.info("Empresa {} excluida (id={}).", empresa.getRazaoSocial(), id);
    }

    /**
     * Busca a entidade, devolvendo erro claro se nao existir.
     */
    private Empresa buscarEntidade(Integer id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Empresa com id " + id + " nao encontrada."));
    }

    /**
     * Remove tudo que nao for digito.
     * Ex.: "11.422.377/0001-55" -> "11422377000155"
     */
    private String somenteDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    /**
     * Converte a entidade no DTO de resposta.
     *
     * ATENCAO: o campo "senha" NAO e copiado para a resposta.
     * E o que garante que nem o hash escape da API.
     */
    private EmpresaResponse paraResponse(Empresa empresa) {
        return EmpresaResponse.builder()
                .id(empresa.getId())
                .razaoSocial(empresa.getRazaoSocial())
                .cnpj(empresa.getCnpj())
                .logadouro(empresa.getLogadouro())
                .municipio(empresa.getMunicipio())
                .numero(empresa.getNumero())
                .complemento(empresa.getComplemento())
                .quantidade(empresa.getQuantidade())
                .cep(empresa.getCep())
                .telefone(empresa.getTelefone())
                .email(empresa.getEmail())
                .site(empresa.getSite())
                .usuario(empresa.getUsuario())
                .createdAt(empresa.getCreatedAt())
                .updatedAt(empresa.getUpdatedAt())
                .build();
    }
}
