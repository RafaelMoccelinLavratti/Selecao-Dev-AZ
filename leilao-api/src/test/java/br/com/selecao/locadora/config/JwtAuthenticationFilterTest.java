package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.Papeis;
import br.com.selecao.locadora.business.TokenJwtBO;
import br.com.selecao.locadora.business.TokenRevogadoBO;
import br.com.selecao.locadora.business.UsuarioAutenticado;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do filtro de autenticacao por token JWT.
 *
 * O filtro e' a fronteira entre o token que o cliente apresenta e a
 * permissao que a aplicacao concede. O que interessa aqui:
 *
 *   - token de sessao ja revogada e' recusado (o logout precisa
 *     encerrar a sessao de verdade);
 *   - token de empresa sem o vinculo com a empresa e' recusado (um
 *     token assim cairia no caminho "sem filtro" e veria tudo).
 */
class JwtAuthenticationFilterTest {

    private static final String FAMILIA = "familia-de-teste";
    private static final String TOKEN = "token.jwt.assinado";

    private JwtAuthenticationFilter filtro;

    private TokenJwtBO tokenJwtBO;
    private TokenRevogadoBO tokenRevogadoBO;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /**
     * A cadeia de filtros: ela so precisa registrar que foi chamada.
     */
    private boolean cadeiaExecutada;

    @BeforeEach
    void preparar() {

        tokenJwtBO = mock(TokenJwtBO.class);
        tokenRevogadoBO = mock(TokenRevogadoBO.class);

        filtro = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filtro, "tokenJwtBO", tokenJwtBO);
        ReflectionTestUtils.setField(filtro, "tokenRevogadoBO", tokenRevogadoBO);

        request = new MockHttpServletRequest("GET", "/empresas");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        response = new MockHttpServletResponse();

        cadeiaExecutada = false;
    }

    /**
     * Limpa o contexto depois de cada teste.
     */
    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Um token de empresa completo (com familia, empresa e papel).
     */
    private Claims tokenDeEmpresa() {

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("admin.aurora");
        when(claims.get(TokenJwtBO.CLAIM_FAMILIA)).thenReturn(FAMILIA);
        when(claims.get(TokenJwtBO.CLAIM_EMPRESA)).thenReturn(1);
        when(claims.get(TokenJwtBO.CLAIM_PAPEL)).thenReturn(Papeis.EMPRESA);

        return claims;
    }

    /**
     * Roda o filtro com a cadeia de exemplo.
     */
    private void executar() throws Exception {

        FilterChain cadeia = (req, res) -> cadeiaExecutada = true;

        filtro.doFilter(request, response, cadeia);
    }

    /**
     * Teste 1: token valido de empresa autentica e leva o vinculo.
     *
     * E o que permite ao controle de acesso saber de qual empresa e' a
     * sessao.
     */
    @Test
    @DisplayName("Token de empresa autentica e leva o vinculo com a empresa")
    void tokenDeEmpresaAutenticaComOVinculo() throws Exception {

        Claims claims = tokenDeEmpresa();

        when(tokenJwtBO.validarTokenAcesso(TOKEN)).thenReturn(claims);
        when(tokenRevogadoBO.familiaRevogada(FAMILIA)).thenReturn(false);

        executar();

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(autenticacao, "A requisicao deveria ter sido autenticada.");
        assertTrue(autenticacao.getPrincipal() instanceof UsuarioAutenticado,
                "O principal precisa ser um UsuarioAutenticado, com o vinculo com a empresa.");

        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacao.getPrincipal();

        assertEquals("admin.aurora", usuario.getUsername());
        assertEquals(Integer.valueOf(1), usuario.getEmpresaId());
        assertEquals(Papeis.EMPRESA, usuario.getPapel());
    }

    /**
     * Teste 2: token de sessao revogada e' recusado.
     *
     * E o que faz o logout encerrar a sessao: o token ainda esta dentro
     * do prazo, mas a sessao foi encerrada.
     */
    @Test
    @DisplayName("Token de sessao revogada e' recusado com 401")
    void tokenDeSessaoRevogadaERecusado() throws Exception {

        Claims claims = tokenDeEmpresa();

        when(tokenJwtBO.validarTokenAcesso(TOKEN)).thenReturn(claims);
        when(tokenRevogadoBO.familiaRevogada(FAMILIA)).thenReturn(true);

        executar();

        assertEquals(401, response.getStatus(),
                "Um token de sessao revogada nao pode ser aceito.");
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "O contexto de seguranca tem de ficar limpo apos a recusa.");
    }

    /**
     * Teste 3: token de empresa sem o vinculo com a empresa e' recusado.
     *
     * Sem esse id, o servidor nao sabe o que a pessoa pode ver. E o
     * pior erro desta camada seria tratar o token como "sem
     * restricao" - ele passaria a ver tudo.
     */
    @Test
    @DisplayName("Token de empresa sem o vinculo e' recusado com 401")
    void tokenDeEmpresaSemVinculoERecusado() throws Exception {

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("admin.aurora");
        when(claims.get(TokenJwtBO.CLAIM_FAMILIA)).thenReturn(FAMILIA);
        when(claims.get(TokenJwtBO.CLAIM_EMPRESA)).thenReturn(null);
        when(claims.get(TokenJwtBO.CLAIM_PAPEL)).thenReturn(Papeis.EMPRESA);

        when(tokenJwtBO.validarTokenAcesso(TOKEN)).thenReturn(claims);
        when(tokenRevogadoBO.familiaRevogada(FAMILIA)).thenReturn(false);

        executar();

        assertEquals(401, response.getStatus(),
                "Token sem vinculo com a empresa nao pode ser aceito.");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Teste 4: o administrador nao tem vinculo com empresa - e isso e'
     * legitimo.
     *
     * O teste anterior nao pode negar o administrador por engano: e' ele
     * que ve tudo.
     */
    @Test
    @DisplayName("Token de administrador e' aceito mesmo sem vinculo com empresa")
    void tokenDeAdministradorEAceito() throws Exception {

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("admin");
        when(claims.get(TokenJwtBO.CLAIM_FAMILIA)).thenReturn(FAMILIA);
        when(claims.get(TokenJwtBO.CLAIM_EMPRESA)).thenReturn(null);
        when(claims.get(TokenJwtBO.CLAIM_PAPEL)).thenReturn(Papeis.ADMIN);

        when(tokenJwtBO.validarTokenAcesso(TOKEN)).thenReturn(claims);
        when(tokenRevogadoBO.familiaRevogada(FAMILIA)).thenReturn(false);

        executar();

        assertTrue(cadeiaExecutada, "O administrador deve passar pela cadeia.");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Teste 5: requisicao sem cabecalho de autorizacao nao e' tocada.
     *
     * Quem decide o que pode passar e' o Spring Security, nao o filtro.
     */
    @Test
    @DisplayName("Requisicao sem token nao e' autenticada pelo filtro")
    void requisicaoSemTokenNaoEAutenticada() throws Exception {

        MockHttpServletRequest semToken = new MockHttpServletRequest("GET", "/empresas");
        MockHttpServletResponse respostaSemToken = new MockHttpServletResponse();

        filtro.doFilter(semToken, respostaSemToken, (req, res) -> cadeiaExecutada = true);

        assertTrue(cadeiaExecutada, "A requisicao tem de seguir a cadeia.");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenJwtBO, never()).validarTokenAcesso(anyString());
    }

    /**
     * Teste 6: token invalido devolve 401 em JSON.
     */
    @Test
    @DisplayName("Token invalido devolve 401 em JSON")
    void tokenInvalidoDevolve401EmJson() throws Exception {

        when(tokenJwtBO.validarTokenAcesso(TOKEN))
                .thenThrow(new TokenInvalidoException("Token inválido ou expirado."));

        executar();

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().contains("application/json"),
                "A resposta de erro tem de ser JSON, nao uma pagina HTML.");
        assertTrue(response.getContentAsString().contains("\"status\":401"));
    }
}
