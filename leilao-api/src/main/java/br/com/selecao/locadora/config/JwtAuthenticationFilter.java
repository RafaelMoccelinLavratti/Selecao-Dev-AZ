package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.TokenJwtBO;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

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

            // Autentica o usuario no contexto de seguranca.
            UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(
                            usuario,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

            autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(autenticacao);

        } catch (TokenInvalidoException e) {

            // Token invalido ou expirado. O log guarda o detalhe; o
            // cliente recebe um 401 padronizado.
            LOG.debug("JWT | Token recusado na requisicao: {}", e.getMessage());

            SecurityContextHolder.clearContext();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":401,\"erro\":\"Nao autorizado\","
                            + "\"mensagem\":\"Token invalido ou expirado.\"}");

            return;
        }

        chain.doFilter(request, response);
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
