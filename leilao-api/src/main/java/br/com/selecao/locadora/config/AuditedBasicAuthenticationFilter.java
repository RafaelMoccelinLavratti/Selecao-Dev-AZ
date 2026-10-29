package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * ============================================================================
 *  FILTRO: AUTENTICACAO BASICA COM AUDITORIA
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE EXISTE UMA SUBCLASSE?
 *  ------------------------------------------------------
 *  No Spring Security 5.7, o BasicAuthenticationFilter NAO possui
 *  "setFailureHandler()" nem "setAuthenticationEntryPoint()". O handler de
 *  falha e definido no construtor e nao pode ser trocado pela configuracao
 *  (o HttpBasicConfigurer expoe apenas realmName, authenticationEntryPoint
 *  e authenticationDetailsSource).
 *
 *  Isso e um problema para nos, porque o handler padrao do Spring devolve
 *  uma PAGINA HTML de erro - e o nosso frontend espera JSON no mesmo
 *  formato do GlobalExceptionHandler. Sem isso, o axios do frontend receberia
 *  um HTML onde esperava JSON e a tela mostraria um erro generico.
 *
 *  A solucao e ESTENDER a classe e sobrescrever dois metodos protegidos:
 *
 *    - onUnsuccessfulAuthentication: e chamado quando as credenciais sao
 *      recusadas. A gente delega ao AuthenticationAuditHandler, que
 *      registra a tentativa, conta a falha e devolve 401 ou 429 em JSON.
 *
 *    - onSuccessfulAuthentication: e chamado quando as credenciais sao
 *      aceitas. A gente chama o comportamento original (super) e registra
 *      o acesso bem-sucedido na trilha de auditoria.
 *
 *  ------------------------------------------------------
 *  UMA REGRA DE OURO QUE ESTA CLASSE RESPEITA
 *  ------------------------------------------------------
 *  Este filtro NUNCA escreve no corpo da resposta em caso de SUCESSO.
 *
 *  No HTTP Basic, o navegador reenvia o cabecalho "Authorization: Basic ..."
 *  em TODAS as requisicoes, entao o metodo de sucesso e chamado a cada
 *  requisicao. Se ele escrevesse algo na resposta, a resposta seria
 *  combinada (commit) antes do controller rodar - e o controller nunca
 *  seria chamado. O resultado seria: TODO endpoint devolvendo a resposta de
 *  autenticacao em vez do dado pedido. Um bug que so apareceria em
 *  producao (em desenvolvimento a autenticacao esta desligada).
 *
 *  No caso de FALHA, escrever na resposta e seguro e desejavel: a
 *  requisicao ja foi barrada, nao ha controller para responder.
 *
 *  ------------------------------------------------------
 *  POR QUE O CONTEXTO DE SEGURANCA E LIMPO APOS A FALHA?
 *  ------------------------------------------------------
 *  Se uma requisicao falhar na autenticacao depois de outra ter dado
 *  certo, o contexto de seguranca poderia guardar uma autenticacao
 *  "solta" na thread. Limpando o contexto, garantimos que a proxima
 *  requisicao que usar essa thread comece do zero.
 * ============================================================================
 */
public class AuditedBasicAuthenticationFilter extends BasicAuthenticationFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuditedBasicAuthenticationFilter.class);

    /**
     * Handler responsavel pela resposta de falha (401/429 em JSON) e
     * pelo registro da tentativa no log de auditoria.
     */
    private final AuthenticationAuditHandler authenticationAuditHandler;

    /**
     * Servico que controla o numero de tentativas de login.
     * Usado para zerar o contador de falhas de quem acabou de entrar
     * com a senha correta.
     */
    private final LoginAttemptService loginAttemptService;

    /**
     * Construtor do filtro.
     *
     * @param authenticationManager       objeto que sabe validar
     *                                    usuario e senha (confere o
     *                                    hash com o BCrypt)
     * @param authenticationFailureHandler handler de falha auditado
     * @param loginAttemptService         controle de tentativas de login
     */
    public AuditedBasicAuthenticationFilter(AuthenticationManager authenticationManager,
                                            AuthenticationAuditHandler authenticationAuditHandler,
                                            LoginAttemptService loginAttemptService) {

        super(authenticationManager);

        this.authenticationAuditHandler = authenticationAuditHandler;
        this.loginAttemptService = loginAttemptService;

        // A API e STATELESS: nao ha sessao de servidor, portanto nada
        // deve ser guardado em repositorio de contexto. O NullSecurity-
        // ContextRepository faz exatamente isso: nao guarda nada.
        SecurityContextRepository contextoSemSessao = new NullSecurityContextRepository();
        setSecurityContextRepository(contextoSemSessao);
    }

    /**
     * Chamado quando as credenciais foram ACEITAS.
     *
     * Delega ao comportamento original (que coloca o usuario no contexto
     * de seguranca) e registra o evento na trilha de auditoria.
     *
     * IMPORTANTE: nao escreve NADA na resposta HTTP.
     *
     * @param request     requisicao HTTP
     * @param response    resposta HTTP
     * @param authResult  objeto de autenticacao com os dados do usuario
     * @throws IOException se occurer erro de leitura/escrita
     */
    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response,
                                              Authentication authResult) throws IOException {

        // Comportamento original do Spring: coloca a autenticacao no
        // contexto de seguranca da thread.
        super.onSuccessfulAuthentication(request, response, authResult);

        // Registro na trilha de auditoria. E aqui (e nao no controller)
        // porque este e o ponto exato em que sabemos que a credencial
        // foi conferida com sucesso.
        String usuario = authResult == null ? "desconhecido" : authResult.getName();

        LOG.info("AUDITORIA | AUTENTICACAO_CONFIRMADA | usuario={}", usuario);

        // Zera o contador de falhas deste usuario: quem entrou com a senha
        // certa nao fica bloqueado por tentativas anteriores.
        loginAttemptService.registrarSucesso(usuario);

        // Nenhuma escrita na resposta: quem responde e o controller.
    }

    /**
     * Chamado quando as credenciais foram RECUSADAS.
     *
     * Repare que este metodo NAO escreve na resposta. Ele apenas:
     *  1) limpa o contexto de seguranca da thread;
     *  2) registra a tentativa no log de auditoria e conta a falha;
     *  3) MARCA a requisicao com o resultado.
     *
     * Quem escreve a resposta (401 ou 429) e o
     * JsonAuthenticationEntryPoint, chamado pelo ExceptionTranslationFilter
     * no fim da cadeia de filtros. O motivo de existir apenas UM escritor
     * de resposta esta explicado em detalhe no AuthenticationAuditHandler.
     *
     * @param request requisicao HTTP
     * @param response resposta HTTP
     * @param failed  a excecao de autenticacao (senha errada, usuario
     *                inexistente, etc.)
     * @throws IOException se occurer erro de leitura/escrita
     */
    @Override
    protected void onUnsuccessfulAuthentication(HttpServletRequest request,
                                                HttpServletResponse response,
                                                AuthenticationException failed) throws IOException {

        // Limpa o contexto de seguranca da thread: garante que nenhuma
        // autenticacao "solta" sobre para a proxima requisicao.
        SecurityContextHolder.clearContext();

        // Registra a tentativa, conta a falha e marca a requisicao.
        authenticationAuditHandler.registrarFalha(request, failed);
    }
}
