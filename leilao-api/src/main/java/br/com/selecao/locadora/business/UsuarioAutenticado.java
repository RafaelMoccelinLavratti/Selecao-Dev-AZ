package br.com.selecao.locadora.business;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * O usuario autenticado, com o vinculo com a empresa.
 *
 * O Spring Security precisa de um "UserDetails" para representar quem
 * esta autenticado. O padrao da biblioteca (a classe "User") so sabe o
 * nome e a senha - e nao sabe de qual empresa a pessoa e. Aqui ele sabe.
 *
 * O id da empresa e o que fecha a porta do acesso cruzado: sem ele, um
 * usuario autenticado veria os dados de todas as empresas, porque o
 * unico teste de acesso seria "esta autenticado?".
 *
 * Quando o id da empresa e nulo, o usuario e o administrador da API
 * (papel ROLE_ADMIN), que nao pertence a nenhuma empresa e ve tudo.
 */
public class UsuarioAutenticado implements UserDetails {

    private static final long serialVersionUID = 1L;

    /**
     * Nome do usuario.
     */
    private final String usuario;

    /**
     * Hash da senha.
     *
     * No fluxo por token JWT nao ha senha na sessao (o token ja provou
     * a identidade), mas o UserDetails exige o campo.
     */
    private final String senha;

    /**
     * Id da empresa do usuario. Nulo para o administrador.
     */
    private final Integer empresaId;

    /**
     * Papel do usuario (ROLE_ADMIN ou ROLE_EMPRESA).
     */
    private final String papel;

    /**
     * Cria o usuario autenticado.
     *
     * @param usuario   nome do usuario
     * @param senha     hash da senha
     * @param empresaId id da empresa, ou nulo para o administrador
     * @param papel     papel de acesso
     */
    public UsuarioAutenticado(String usuario, String senha, Integer empresaId, String papel) {
        this.usuario = usuario;
        this.senha = senha;
        this.empresaId = empresaId;
        this.papel = papel;
    }

    /**
     * Cria o usuario autenticado como administrador da API.
     *
     * @param usuario nome do usuario
     * @param senha   hash da senha
     * @return o administrador, sem vinculo com empresa nenhuma
     */
    public static UsuarioAutenticado administrador(String usuario, String senha) {
        return new UsuarioAutenticado(usuario, senha, null, Papeis.ADMIN);
    }

    /**
     * Cria o usuario autenticado como usuario de uma empresa.
     *
     * @param usuario   nome do usuario
     * @param senha     hash da senha
     * @param empresaId id da empresa
     * @return o usuario da empresa
     */
    public static UsuarioAutenticado daEmpresa(String usuario, String senha, Integer empresaId) {
        return new UsuarioAutenticado(usuario, senha, empresaId, Papeis.EMPRESA);
    }

    /**
     * O id da empresa do usuario.
     *
     * @return o id da empresa, ou nulo se for o administrador
     */
    public Integer getEmpresaId() {
        return empresaId;
    }

    /**
     * O papel do usuario.
     *
     * @return ROLE_ADMIN ou ROLE_EMPRESA
     */
    public String getPapel() {
        return papel;
    }

    /**
     * Diz se o usuario e o administrador da API.
     *
     * @return true se o papel for ROLE_ADMIN
     */
    public boolean ehAdministrador() {
        return Papeis.ADMIN.equals(papel);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(papel));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return usuario;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
