package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.Papeis;
import br.com.selecao.locadora.business.TokenJwtBO;
import br.com.selecao.locadora.business.TokenRevogadoBO;
import br.com.selecao.locadora.business.UsuarioAutenticado;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Autenticacao por token JWT.
 *
 * Le o cabecalho Authorization, valida a assinatura e, se o token for
 * valido, coloca o usuario no contexto de seguranca.
 *
 * Token invalido nao e bloqueado aqui: quem decide o que pode passar e o
 * Spring Security. A excecao e o token malformado no cabecalho, que e
 * erro do cliente e responde 401 na hora.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * Prefixo do esquema de autorizacao definido na RFC 6750.
     */
    private static final String PREFIXO_BEARER = "Bearer ";

    /**
     * Nome do cabecalho que carrega o token.
     */
    private static final String CABECALHO_AUTHORIZATION = "Authorization";

    /**
     * Servico de validacao dos tokens.
     */
    @Autowired
    private TokenJwtBO tokenJwtBO;

    /**
     * Lista de bloqueio das sessoes revogadas.
     *
     * E o que faz o logout encerrar a sessao de verdade: sem ela, o
     * token de acesso continua valido ate expirar.
     *
     * O "required = false" existe para os testes, que montam o filtro
     * sem a lista de bloqueio.
     */
    @Autowired(required = false)
    private TokenRevogadoBO tokenRevogadoBO;

    /**
     * Processa a requisicao, tentando autenticar pelo token JWT.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extrairToken(request);

        if (token == null) {
            // Sem token: segue o fluxo normal. O SecurityConfig decide se
            // a rota exige autenticacao.
            chain.doFilter(request, response);
            return;
        }

        try {

            Claims claims = tokenJwtBO.validarTokenAcesso(token);
            String usuario = claims.getSubject();

            // A sessao pode ter sido encerrada depois que este token foi
            // emitido - por logout, ou por deteccao de reuso. A lista de
            // bloqueio diz se ainda vale.
            //
            // E por isso que o token carrega a familia (claim "fam"): a
            // revogacao e sempre em lote, e o filtro precisa saber a
            // qual lote o token pertence.
            if (tokenRevogadoBO != null && tokenRevogadoBO.familiaRevogada(familiaDoToken(claims))) {
                LOG.debug("JWT | Token de sessao revogada recusado: {}", usuario);
                responderNaoAutorizado(response);
                return;
            }

            // Autentica o usuario no contexto de seguranca.
            //
            // O principal e' um UsuarioAutenticado, e nao uma String: e
            // ele que carrega o vinculo com a empresa e o papel, sem os
            // quais o controle de acesso nao saberia o que autorizar.
            Integer empresaId = empresaDoToken(claims);
            String papel = papelDoToken(claims);

            // Token de empresa sem o id da empresa e' um estado
            // inconsistente: nao da para saber o que a pessoa pode ver.
            // A recusa acontece aqui, na autenticacao, e nao no meio da
            // autorizacao - porque um token assim, se passasse, cairia
            // no caminho de "sem filtro" e veria tudo.
            if (!Papeis.ADMIN.equals(papel) && empresaId == null) {
                LOG.warn("JWT | Token sem vinculo de empresa recusado: {}", usuario);
                responderNaoAutorizado(response);
                return;
            }

            UsuarioAutenticado autenticado = new UsuarioAutenticado(
                    usuario,
                    null,
                    empresaId,
                    papel);

            UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(
                            autenticado,
                            null,
                            autenticado.getAuthorities());

            autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(autenticacao);

        } catch (TokenInvalidoException e) {

            // Token invalido ou expirado. O log guarda o detalhe; o
            // cliente recebe um 401 padronizado.
            LOG.debug("JWT | Token recusado na requisicao: {}", e.getMessage());

            SecurityContextHolder.clearContext();

            responderNaoAutorizado(response);

            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Responde 401 em JSON.
     *
     * O padrao do Spring seria uma pagina HTML, que quebraria o
     * frontend. O formato e o mesmo do GlobalExceptionHandler.
     */
    private void responderNaoAutorizado(HttpServletResponse response) throws IOException {

        SecurityContextHolder.clearContext();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"erro\":\"Nao autorizado\","
                        + "\"mensagem\":\"Token invalido ou expirado.\"}");
    }

    /**
     * A familia de sessao que vem dentro do token.
     */
    private String familiaDoToken(Claims claims) {

        Object valor = claims.get(TokenJwtBO.CLAIM_FAMILIA);

        return valor instanceof String ? (String) valor : null;
    }

    /**
     * O id da empresa que vem dentro do token.
     */
    private Integer empresaDoToken(Claims claims) {

        Object valor = claims.get(TokenJwtBO.CLAIM_EMPRESA);

        if (valor instanceof Number) {
            return ((Number) valor).intValue();
        }

        return null;
    }

    /**
     * O papel que vem dentro do token.
     *
     * Token sem papel e' tratado como usuario de empresa, nunca como
     * administrador: e o erro que mais custa, porque libera o acesso
     * total.
     */
    private String papelDoToken(Claims claims) {

        Object valor = claims.get(TokenJwtBO.CLAIM_PAPEL);

        return valor instanceof String ? (String) valor : Papeis.EMPRESA;
    }

    /**
     * Extrai o token do cabecalho Authorization.
     *
     * @return o token, ou null se o cabecalho nao existir ou nao for
     *         no padrao "Bearer"
     */
    private String extrairToken(HttpServletRequest request) {

        String cabecalho = request.getHeader(CABECALHO_AUTHORIZATION);

        if (cabecalho == null || !cabecalho.startsWith(PREFIXO_BEARER)) {
            return null;
        }

        String token = cabecalho.substring(PREFIXO_BEARER.length()).trim();

        return token.isEmpty() ? null : token;
    }
}
