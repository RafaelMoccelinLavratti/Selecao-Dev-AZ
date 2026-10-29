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
 * ============================================================================
 *  FILTRO: AUTENTICACAO POR TOKEN JWT
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE ESTE FILTRO FAZ?
 *  ------------------------------------------------------
 *  Ele le o cabecalho "Authorization" de cada requisicao. Se houver um
 *  token no padrao "Bearer <token>", ele valida a assinatura e, se o
 *  token for valido, coloca o usuario no contexto de seguranca - de modo
 *  que as regras de acesso mais abaixo (no SecurityConfig) saibam QUEM
 *  esta chamando.
 *
 *  Se nao houver token, o filtro nao faz nada: e o SecurityConfig que
 *  decide se a rota exige autenticacao. Isso mantem o filtro simples e
 *  deixa a politica de acesso em um unico lugar.
 *
 *  ------------------------------------------------------
 *  POR QUE UM FILTRO E NAO UM INTERCEPTOR?
 *  ------------------------------------------------------
 *  A diferenca e a ordem de execucao. O filtro roda ANTES de o Spring
 *  MVC saber qual controller vai atender a requisicao - ou seja, antes de
 *  qualquer codigo da aplicacao. Isso e importante por dois motivos:
 *
 *    1) uma rota bloqueada nem chega a executar a regra de negocio
 *       (nem a fazer uma consulta no banco);
 *    2) o token e lido e validado uma unica vez, e o resultado fica
 *       disponivel para todo o resto da requisicao.
 *
 *  ------------------------------------------------------
 *  O QUE ACONTECE COM UM TOKEN INVALIDO?
 *  ------------------------------------------------------
 *  O filtro DEIXA PASSAR. Ele nao bloqueia a requisicao.
 *
 *  Isso parece errado, mas e o comportamento correto: quem decide se a
 *  requisicao pode ou nao seguir adiante e o Spring Security, no
 *  FilterSecurityInterceptor. O filtro apenas NAO autentica ninguem.
 *
 *  Se o filtro devolvesse 401 ali, teriamos dois lugaresrespondevendo
 *  401 (o filtro e o entry point), com o risco de a resposta ser
 *  escrita duas vezes - exatamente o bug que o projeto ja enfrentou
 *  com o HTTP Basic, em que o handler de falha e o entry point
 *  escreviam a resposta e o status do segundo sobrescrevia o do
 *  primeiro (401 no lugar do 429 de "muitas tentativas").
 *
 *  A UNICA excecao e o token malformado no cabecalho: nesse caso o
 *  cliente claramente QUERIA se autenticar e errou o formato. O filtro
 *  responde 401 imediatamente, com a mesma formatacao JSON do resto da
 *  API.
 * ============================================================================
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
