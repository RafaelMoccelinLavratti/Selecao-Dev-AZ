package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.UsuarioAutenticado;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.repository.EmpresaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * Busca o usuario autenticado, no banco ou na configuracao.
 *
 * Sao duas origens, e elas nao se confundem:
 *
 *   - o ADMINISTRADOR da API vem da configuracao (app.security.username
 *     e app.security.password). Ele nao pertence a nenhuma empresa e
 *     nao tem linha na tabela tb_empresa.
 *
 *   - o USUARIO DE UMA EMPRESA vem da tabela tb_empresa, pelas colunas
 *     "usuario" e "senha". A senha esta guardada como hash (Argon2id, ou
 *     BCrypt nos registros antigos), e nunca em texto puro.
 *
 * Antes desta classe, o projeto so tinha o administrador: as colunas
 * "usuario" e "senha" da tb_empresa eram gravadas, mas nao serviam para
 * ninguem entrar. O modelo de dados era multi-empresa; o controle de
 * acesso nao.
 */
@Component
public class UsuarioDetailsServiceEmpresa implements UserDetailsService {

    private static final Logger LOG = LoggerFactory.getLogger(UsuarioDetailsServiceEmpresa.class);

    /**
     * Codificador de senha, usado para montar o hash do administrador.
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Repositorio das empresas.
     *
     * O "ObjectProvider" e' usado porque este servico tambem roda nos
     * testes de seguranca, que sobem so a camada web e nao tem banco.
     * Sem repositorio ali, so o administrador existe - que e' exatamente
     * o que esses testes precisam.
     */
    @Autowired
    private ObjectProvider<EmpresaRepository> empresas;

    /**
     * Nome do administrador da API.
     */
    @Value("${app.security.username:admin}")
    private String usuarioAdministrador;

    /**
     * Senha do administrador da API, em texto puro.
     *
     * Ela e hasheada aqui, na subida, para nunca ficar guardada em
     * claro na memoria depois disso.
     */
    @Value("${app.security.password:troque-esta-senha}")
    private String senhaAdministrador;

    /**
     * Hash da senha do administrador.
     *
     * Calculado uma vez, na subida. Hashar a senha a cada autenticacao
     * seria caro: o Argon2id e' lento de proposito, e um ataque de
     * forca bruta contra o administrador transformaria cada tentativa
     * em dois calculos de hash em vez de um.
     */
    private String hashAdministrador;

    /**
     * Prepara o hash do administrador na subida.
     */
    @PostConstruct
    public void inicializar() {
        this.hashAdministrador = passwordEncoder.encode(senhaAdministrador);
    }

    /**
     * Busca o usuario pelo nome.
     *
     * @param nome nome do usuario informado na autenticacao
     * @return os dados do usuario
     * @throws UsernameNotFoundException se nao houver usuario com esse nome
     */
    @Override
    public UserDetails loadUserByUsername(String nome) throws UsernameNotFoundException {

        if (nome == null || nome.trim().isEmpty()) {
            throw new UsernameNotFoundException("Nome de usuário vazio.");
        }

        // O administrador tem prioridade: e' o unico acesso garantido,
        // mesmo que a tabela de empresas nao esteja disponivel.
        if (usuarioAdministrador != null && usuarioAdministrador.equals(nome)) {
            return UsuarioAutenticado.administrador(nome, hashAdministrador);
        }

        EmpresaRepository repositorio = empresas.getIfAvailable();

        if (repositorio == null) {
            // Sem banco (testes de seguranca): so existe o administrador.
            throw new UsernameNotFoundException("Usuário não encontrado: " + nome);
        }

        Optional<Empresa> empresa = repositorio.findByUsuario(nome);

        if (!empresa.isPresent()) {
            throw new UsernameNotFoundException("Usuário não encontrado: " + nome);
        }

        Empresa cadastro = empresa.get();

        // Empresa sem senha cadastrada nao consegue entrar: e' um
        // cadastro feito pela administracao, que ainda nao criou o
        // acesso do usuario.
        if (cadastro.getSenha() == null || cadastro.getSenha().isEmpty()) {
            LOG.warn("AUTENTICACAO | Empresa sem senha cadastrada | usuario={} empresa={}",
                    nome, cadastro.getId());
            throw new UsernameNotFoundException("Usuário não encontrado: " + nome);
        }

        return UsuarioAutenticado.daEmpresa(nome, cadastro.getSenha(), cadastro.getId());
    }
}
