package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * ============================================================================
 *  FILTRO: TRILHA DE AUDITORIA DAS OPERACOES DE ALTERACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE ESTE FILTRO FAZ?
 *  ------------------------------------------------------
 *  O AuthenticationAuditHandler registra os ACESSOS (login). Este filtro
 *  registra as ALTERACOES DE DADOS: todo POST, PUT, DELETE e PATCH deixa
 *  um registro de QUEM fez, QUANDO, EM QUE ENDERECO e COM QUE RESULTADO.
 *
 *  Exemplo de linha gerada:
 *
 *    AUDITORIA | MUTACAO | metodo=POST caminho=/empresas usuario=admin
 *               ip=127.0.0.1 status=201 duracao_ms=45
 *
 *  ------------------------------------------------------
 *  POR QUE REGISTRAR SÓ AS MUTACOES?
 *  ------------------------------------------------------
 *  Um log com TODAS as requisicoes (inclusive as de leitura) cresceria
 *  muito rapido e poluiria o arquivo, escondendo justamente o que
 *  importa. As operacoes de leitura nao alteram estado - elas sao
 *  "curiosidade" do usuario. As de alteracao sao o que a auditoria
 *  precisa provar: quem mexeu no dado, quando e o que deu.
 *
 *  O padrao e o mesmo de uma trilha de auditoria de banco de dados
 *  (tabela de log com INSERT/UPDATE/DELETE), que e exatamente o que
 *  ferramentas como pgAudit ou o recurso de auditoria do PostgreSQL
 *  fazem no lado do banco.
 *
 *  ------------------------------------------------------
 *  UMA REGRA DE OURO: O CORPO DA REQUISICAO NAO E REGISTRADO
 *  ------------------------------------------------------
 *  Seria muito facil (e muito tentador) registrar o corpo JSON da
 *  requisicao. Nao fazemos, por um motivo de seguranca direto:
 *
 *  O cadastro de empresa (POST /empresas) recebe o campo "senha" no
 *  corpo. Se o filtro registrasse o corpo, a senha em texto claro
 *  passaria a estar gravada em arquivo de log - um lugar muito mais
 *  acessivel e muito menos protegido que o banco de dados.
 *
 *  Por isso o filtro registra APENAS metadados da requisicao (metodo,
 *  caminho, quem, de onde, status e tempo). Nada do conteudo.
 *
 *  Pela mesma razao, a query string (?senha=...) tambem nao e registrada:
 *  e muito comum um desenvolvedor colocar parametro sensivel na URL por
 *  descuido.
 *
 *  ------------------------------------------------------
 *  COMO SABE QUEM E O USUARIO?
 *  ------------------------------------------------------
 *  O filtro roda DEPOIS do FilterSecurityInterceptor, ou seja, depois
 *  que o Spring ja decidiu se a requisicao estava autorizada. nesse
 *  momento, o SecurityContextHolder guarda o objeto Authentication com
 *  o usuario autenticado.
 *
 *  Em desenvolvimento (autenticacao desligada), nao existe usuario no
 *  contexto - e o log usa "anonimo", o que continua sendo um registro
 *  util.
 * ============================================================================
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

        // Nível de log conforme o resultado: erro merece destaque.
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
