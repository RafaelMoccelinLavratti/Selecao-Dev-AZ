package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.AuditoriaBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filtro que registra as alteracoes de dados na trilha de auditoria.
 *
 * Roda depois do FilterSecurityInterceptor, para que o usuario ja
 * autenticado esteja no contexto. So POST, PUT, DELETE e PATCH sao
 * registrados - as leituras nao interessam e inundariam o log.
 *
 * O corpo da requisicao nunca e gravado: ele pode conter senha, CNPJ e
 * outros dados que nao devem ficar no log.
 */
@Component
public class AuditTrailFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuditTrailFilter.class);

    /**
     * Indica se a aplicacao esta atras de um proxy reverso confiavel.
     */
    @Value("${app.security.trust-proxy:false}")
    private boolean confiarEmProxy;

    /**
     * Metodos HTTP que alteram dados e por isso precisam de trilha.
     */
    private static final String[] METODOS_DE_MUTACAO = {"POST", "PUT", "PATCH", "DELETE"};

    /**
     * Camada de negocio que grava a trilha em forma A PROVA DE
     * ADULTERACAO (cadeia de hashes com HMAC).
     */
    @Autowired
    private AuditoriaBO auditoriaBO;

    /**
     * Executa a trilha de auditoria em torno da requisicao.
     *
     * @param request  requisicao HTTP
     * @param response resposta HTTP
     * @param chain    cadeia de filtros seguinte
     * @throws ServletException se occurer erro no processamento
     * @throws IOException      se occurer erro de leitura/escrita
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // So as operacoes que alteram dados entram na trilha.
        if (!ehOperacaoDeMutacao(request)) {
            chain.doFilter(request, response);
            return;
        }

        // Instante de inicio: serve para medir a duracao da operacao.
        long inicio = System.currentTimeMillis();

        // Executa o restante da cadeia (ate o controller).
        chain.doFilter(request, response);

        // Calcula quanto tempo a operacao levou.
        long duracao = System.currentTimeMillis() - inicio;

        // Monta o registro.
        String ip = ClientIpResolver.resolver(request, confiarEmProxy);
        String usuario = usuarioAutenticado();
        int status = response.getStatus();

        // Nivel de log conforme o resultado: erro merece destaque.
        if (status >= 500) {
            LOG.error("AUDITORIA | MUTACAO | metodo={} caminho={} usuario={} ip={} status={} duracao_ms={}",
                    request.getMethod(), request.getRequestURI(), usuario, ip, status, duracao);
        } else if (status >= 400) {
            LOG.warn("AUDITORIA | MUTACAO | metodo={} caminho={} usuario={} ip={} status={} duracao_ms={}",
                    request.getMethod(), request.getRequestURI(), usuario, ip, status, duracao);
        } else {
            LOG.info("AUDITORIA | MUTACAO | metodo={} caminho={} usuario={} ip={} status={} duracao_ms={}",
                    request.getMethod(), request.getRequestURI(), usuario, ip, status, duracao);
        }

        // Grava na trilha selada por hash. O registro acontece em uma
        // TRANSACAO SEPARADA (REQUIRES_NEW) da operacao de negocio: se a
        // gravacao falhar, a operacao do usuario NAO e perdida. E uma
        // falha na trilha tambem nao derruba a requisicao - o AuditoriaBO
        // trata a excecao internamente e registra no log.
        auditoriaBO.registrar(
                usuario,
                ip,
                request.getMethod(),
                request.getRequestURI(),
                status,
                duracao,
                descreverAcao(request),
                null);
    }

    /**
     * Descreve a acao a partir do metodo HTTP e do caminho.
     *
     * A descricao e o que o auditor le: "CRIAR EMPRESA" e muito mais
     * util do que "POST /empresas". A API e' porem a fonte da verdade,
     * entao o caminho completo tambem e gravado (campo "caminho").
     *
     * @param request requisicao HTTP
     * @return a descricao da acao
     */
    private String descreverAcao(HttpServletRequest request) {

        String metodo = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        String caminho = request.getRequestURI() == null ? "" : request.getRequestURI();

        // Pega o ultimo segmento do caminho (a colecao, ex.: "empresas").
        String recurso = caminho;
        int ultimaBarra = caminho.lastIndexOf('/');
        if (ultimaBarra >= 0 && ultimaBarra < caminho.length() - 1) {
            recurso = caminho.substring(ultimaBarra + 1);
        }

        String verbo;

        switch (metodo) {
            case "POST":
                verbo = "CRIAR";
                break;
            case "PUT":
            case "PATCH":
                verbo = "ALTERAR";
                break;
            case "DELETE":
                verbo = "EXCLUIR";
                break;
            default:
                verbo = metodo;
        }

        return verbo + " " + recurso.toUpperCase();
    }

    /**
     * Descobre se o metodo HTTP da requisicao altera dados.
     *
     * @param request requisicao HTTP
     * @return true se for POST, PUT, PATCH ou DELETE
     */
    private boolean ehOperacaoDeMutacao(HttpServletRequest request) {

        String metodo = request.getMethod();

        if (metodo == null) {
            return false;
        }

        for (String metodoDeMutacao : METODOS_DE_MUTACAO) {
            if (metodoDeMutacao.equalsIgnoreCase(metodo)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Pega o usuario autenticado no contexto de seguranca.
     *
     * @return o nome do usuario, ou "anonimo" se nao houver autenticacao
     *         (que e o caso do perfil de desenvolvimento, com a
     *         autenticacao desligada)
     */
    private String usuarioAutenticado() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonimo";
        }

        String nome = authentication.getName();

        // O Spring cria um usuario "anonymousUser" quando nao ha login.
        if (nome == null || "anonymousUser".equals(nome)) {
            return "anonimo";
        }

        return nome;
    }
}
