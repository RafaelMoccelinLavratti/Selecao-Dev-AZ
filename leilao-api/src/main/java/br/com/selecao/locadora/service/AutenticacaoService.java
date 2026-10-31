package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.AutenticacaoBO;
import br.com.selecao.locadora.business.UsuarioAutenticado;
import br.com.selecao.locadora.config.ClientIpResolver;
import br.com.selecao.locadora.config.UsuarioAtual;
import br.com.selecao.locadora.dto.LoginRequest;
import br.com.selecao.locadora.dto.LoginResponse;
import br.com.selecao.locadora.dto.RefreshRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
 * Controller REST da autenticacao.
 *
 * POST /auth/login    - autentica e devolve o par de tokens
 * POST /auth/refresh  - troca um token de renovacao por um par novo
 * POST /auth/logout   - revoga a sessao
 * GET  /auth/me       - devolve quem esta autenticado
 *
 * O login responde 200 e nao 201: ele nao cria um recurso novo, entrega uma
 * credencial temporaria.
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
     * Indica se a autenticacao esta ligada.
     *
     * O frontend precisa saber disso: no perfil de desenvolvimento (a
     * autenticacao desligada de proposito), nao ha login para fazer, e
     * pedir um login que nao existe seria um erro de interface.
     *
     * Por isso o "/auth/me" responde "autenticacaoAtiva": o frontend
     * so' exige login quando a API exige credencial.
     */
    @Value("${app.security.enabled:false}")
    private boolean autenticacaoAtiva;

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
     * Alem do nome, devolve o papel e o id da empresa. Sao eles que
     * permitem ao frontend esconder o que o usuario nao pode acessar
     * (o botao de cadastrar empresa, por exemplo, so aparece para o
     * administrador) - e esconder e' medida de seguranca, e nao so de
     * usabilidade: o backend recusa a operacao de qualquer jeito, mas
     * um botao que da erro e' um botao quebrado.
     *
     * @return os dados do usuario autenticado
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> eu() {

        // O principal autenticado de verdade. O "UsuarioAtual" so' devolve
        // algo quando o contexto tem um "UsuarioAutenticado" - o que
        // exclui tanto o "anonymousUser" quanto o "User" simples.
        UsuarioAutenticado usuario = UsuarioAtual.usuario().orElse(null);

        // O Spring coloca um "AnonymousAuthenticationToken" no contexto
        // quando nao ha credencial. Ele responde "true" para
        // "isAuthenticated()" - mas nao e' uma sessao. Sem este
        // cuidado, o frontend receberia "autenticado: true" de quem
        // nao esta logado, e mostraria a tela principal sem sessao.
        boolean autenticado = usuario != null;

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("usuario", autenticado ? usuario.getUsername() : "anonimo");
        corpo.put("autenticado", autenticado);

        // Diz se a API esta exigindo credencial. E' o que permite ao
        // frontend nao cobrar login de quem so' esta explorando o
        // projeto com a seguranca desligada.
        corpo.put("autenticacaoAtiva", autenticacaoAtiva);

        // O papel e o vinculo com a empresa vem do principal. Quando a
        // autenticacao esta desligada (perfil de desenvolvimento), o
        // principal nao tem essa forma - e a resposta diz "sem vinculo",
        // que o frontend entende como "sem restricao".
        corpo.put("papel", autenticado ? usuario.getPapel() : null);
        corpo.put("empresaId", autenticado ? usuario.getEmpresaId() : null);
        corpo.put("administrador", autenticado && usuario.ehAdministrador());

        return ResponseEntity.ok(corpo);
    }
}
