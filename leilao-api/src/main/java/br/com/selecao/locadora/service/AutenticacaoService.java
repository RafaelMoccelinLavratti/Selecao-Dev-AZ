package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.AutenticacaoBO;
import br.com.selecao.locadora.config.ClientIpResolver;
import br.com.selecao.locadora.dto.LoginRequest;
import br.com.selecao.locadora.dto.LoginResponse;
import br.com.selecao.locadora.dto.RefreshRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 *  CONTROLLER REST: AUTENTICACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  ONDE FICA E POR QUE
 *  ------------------------------------------------------
 *  No pacote "service", como o enunciado determina:
 *
 *      "Controladores REST: pacote service - endpoints dos servicos rest"
 *
 *  Este controller tem UMA responsabilidade: traduzir HTTP em Java.
 *  Ele sabe qual URL responde a qual metodo, como ler o corpo do pedido
 *  e qual status code devolver. NAO sabe nenhuma regra de negocio -
 *  quem decide se a senha esta correta e a camada "business".
 *
 *  ------------------------------------------------------
 *  OS ENDPOINTS
 *  ------------------------------------------------------
 *  POST /auth/login    - autentica e devolve o par de tokens
 *  POST /auth/refresh  - troca um token de renovacao por um par novo
 *  POST /auth/logout   - revoga a sessao
 *  GET  /auth/me       - devolve quem esta autenticado
 *
 *  Os tres primeiros sao PUBLICOS por definicao (e o que permite a um
 *  cliente NAO autenticado obter um token). O ultimo exige autenticacao.
 *
 *  ------------------------------------------------------
 *  O STATUS CODE DE LOGIN
 *  ------------------------------------------------------
 *  Devolvemos 200, e nao 201 (Created). A diferenca nao e preciosismo:
 *  201 quer dizer "criei um recurso novo", e o login nao cria nada - ele
 *  entrega uma credencial temporaria. O recurso "criado" seria o token,
 *  e ele tem um tempo de vida curto demais para ser tratado como recurso
 *  enderecavel.
 * ============================================================================
 */
@RestController
@RequestMapping(value = {"/auth", "/autenticacao"})
public class AutenticacaoService {

    private static final Logger LOG = LoggerFactory.getLogger(AutenticacaoService.class);

    /**
     * Camada de negocio com as regras de autenticacao.
     */
    @Autowired
    private AutenticacaoBO autenticacaoBO;

    /**
     * Indica se a aplicacao esta atras de um proxy reverso confiavel.
     */
    @Value("${app.security.trust-proxy:false}")
    private boolean confiarEmProxy;

    /**
     * POST /auth/login - autentica o usuario e devolve o par de tokens.
     *
     * @param request usuario e senha
     * @param httpRequest requisicao, usada para descobrir o IP de origem
     * @return o par de tokens
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {

        String ip = ClientIpResolver.resolver(httpRequest, confiarEmProxy);

        LoginResponse resposta = autenticacaoBO.autenticar(request, ip);

        return ResponseEntity.ok(resposta);
    }

    /**
     * POST /auth/refresh - renova o token de acesso.
     *
     * @param request o token de renovacao
     * @param httpRequest requisicao, usada para descobrir o IP de origem
     * @return um par novo de tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                  HttpServletRequest httpRequest) {

        String ip = ClientIpResolver.resolver(httpRequest, confiarEmProxy);

        LoginResponse resposta = autenticacaoBO.renovar(request.getTokenRenovacao(), ip);

        return ResponseEntity.ok(resposta);
    }

    /**
     * POST /auth/logout - revoga a sessao do usuario.
     *
     * @param request o token de renovacao da sessao
     * @return 204 (sem conteudo), que e o status correto para uma
     *         operacao que nao devolve nada
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {

        autenticacaoBO.revogar(request.getTokenRenovacao());

        return ResponseEntity.noContent().build();
    }

    /**
     * GET /auth/me - devolve quem esta autenticado.
     *
     * Serve ao frontend para mostrar o nome do usuario logado e para
     * decidir o que ele pode ver.
     *
     * @return os dados do usuario autenticado
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> eu() {

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("usuario", autenticacao != null ? autenticacao.getName() : "anonimo");
        corpo.put("autenticado", autenticacao != null && autenticacao.isAuthenticated());

        return ResponseEntity.ok(corpo);
    }
}
