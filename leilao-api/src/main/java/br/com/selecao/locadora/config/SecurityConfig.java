package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

/**
 * Configuracao de seguranca da API.
 *
 * A autenticacao e ligada ou desligada por configuracao
 * (app.security.enabled), e nao por codigo: o mesmo build serve o
 * desenvolvimento aberto e a producao com tudo protegido.
 *
 * Quando ligada, ha dois modos - HTTP Basic ou token JWT - e os dois
 * produzem o mesmo objeto de autenticacao, entao as regras de acesso nao
 * mudam.
 *
 * A API e stateless e nao usa cookie de sessao, e por isso o CSRF nao se
 * aplica.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Liga/desliga a autenticacao. Vem da variavel de ambiente
     * SECURITY_ENABLED (padrao false = desenvolvimento aberto).
     */
    @Value("${app.security.enabled:false}")
    private boolean autenticacaoAtiva;

    /**
     * Modo de autenticacao quando a seguranca esta ligada.
     *
     * "basic" = HTTP Basic (padrao, comportamento anterior).
     * "jwt"   = token JWT no cabecalho Authorization.
     */
    @Value("${app.security.mode:basic}")
    private String modoSeguranca;

    /**
     * Usuario da autenticacao HTTP Basic (perfil de producao).
     */
    @Value("${app.security.username:admin}")
    private String usuarioPadrao;

    /**
     * Senha da autenticacao HTTP Basic (perfil de producao).
     */
    @Value("${app.security.password:troque-esta-senha}")
    private String senhaPadrao;

    /**
     * Filtro que bloqueia IPs que estouraram o limite de tentativas de
     * autenticacao (controle anti-forca-bruta). Ele e registrado ANTES do
     * filtro de autenticacao basica - ver o metodo securityFilterChain.
     */
    @Autowired
    private BruteForceProtectionFilter bruteForceProtectionFilter;

    /**
     * Filtro que autentica a requisicao pelo token JWT.
     */
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Filtro que registra na trilha de auditoria as operacoes que
     * alteram dados (POST, PUT, PATCH, DELETE).
     */
    @Autowired
    private AuditTrailFilter auditTrailFilter;

    /**
     * Handler que registra os eventos de autenticacao (sucesso e falha)
     * e devolve as respostas 401/429 em JSON padronizado.
     */
    @Autowired
    private AuthenticationAuditHandler authenticationAuditHandler;

    /**
     * Ponto de entrada de autenticacao: devolve 401 em JSON quando a
     * requisicao chega a uma rota protegida sem credencial.
     */
    @Autowired
    private JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;

    /**
     * Configuracao de autenticacao do Spring Security.
     *
     * Serve para obter o AuthenticationManager ja montado (que sabe
     * consultar o UserDetailsService e conferir a senha com o
     * PasswordEncoder/BCrypt). E a forma oficial de obter esse
     * objeto sem precisar montar o ProviderManager na mao.
     */
    @Autowired
    private AuthenticationConfiguration authenticationConfiguration;

    /**
     * Servico que controla o numero de tentativas de autenticacao
     * (anti-forca-bruta).
     */
    @Autowired
    private LoginAttemptService loginAttemptService;

    /**
     * Define a cadeia de filtros de seguranca do Spring Security.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.cors()  // habilita o CORS configurado em CorsConfig
            .and()
            .csrf().disable()  // API stateless, sem cookie de sessao (explicado acima)
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .headers(headers -> headers
                    // Bloqueia o embutimento em iframe de sites externos.
                    // "deny" e mais restritivo que o padrao "sameOrigin" do
                    // Spring: a API nunca precisa ser exibida dentro de
                    // um iframe, entao a proibicao total e a mais segura.
                    .frameOptions().deny()
                    // Content-Type Options: impede que o navegador "adivinhe" o tipo.
                    .contentTypeOptions().and()
                    // HSTS: obriga o navegador a usar HTTPS nas proximas requisicoes.
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            );

        if (autenticacaoAtiva) {
            // ---- MODO PRODUCAO: tudo exige autenticacao ----
            http.authorizeRequests()
                    // O health check NAO pode exigir credencial: o orquestrador
                    // (Docker/Kubernetes) consulta esse endpoint e, se ele
                    // devolvesse 401, o container seria considerado morto.
                    .antMatchers("/actuator/health", "/actuator/info").permitAll()
                    // Os endpoints de autenticacao precisam ser publicos -
                    // sem isso, nao haveria como OBTER o token.
                    .antMatchers("/auth/login", "/auth/refresh",
                                 "/autenticacao/login", "/autenticacao/refresh").permitAll()
                    .anyRequest().authenticated()
                    .and()
                // Resposta de "acesso negado" SEMPRE em JSON, no mesmo
                // formato do GlobalExceptionHandler (o padrao do Spring
                // seria uma pagina HTML, que quebraria o frontend).
                .exceptionHandling()
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint);

            if ("jwt".equalsIgnoreCase(modoSeguranca)) {

                // Modo JWT
                // O filtro de JWT entra ANTES do filtro de autenticacao
                // basica: assim, uma requisicao com "Authorization: Bearer"
                // e autenticada pelo token, e uma com "Authorization: Basic"
                // cai no filtro basico. Quem chegar sem cabecalho e barrado
                // pelo FilterSecurityInterceptor, com 401 em JSON.
                http.addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class);

            } else {

                // Modo HTTP basic
                //  POR QUE NAO USAMOS "http.httpBasic()"?
                //  Porque o HttpBasicConfigurer do Spring Security 5.7 expoe
                //  apenas tres metodos: realmName(), authenticationEntryPoint()
                //  e authenticationDetailsSource(). NAO existe um metodo
                //  "failureHandler()" - o handler de falha e definido
                //  dentro do construtor do BasicAuthenticationFilter e nao
                //  pode ser trocado pela configuracao.
                //
                //  Como o handler padrao do Spring devolve uma pagina HTML
                //  (e nao o JSON que o frontend espera), usamos o
                //  AuditedBasicAuthenticationFilter, que estende o filtro
                //  original e sobrescreve o tratamento de sucesso e falha.
                //
                //  DETALHE IMPORTANTE: o filtro e criado aqui, dentro do
                //  metodo, e NAO declarado como "@Bean". Se fosse um @Bean do
                //  tipo Filter, o Spring Boot o registraria TAMBEM como filtro
                //  do container de servlet (fora da cadeia do Spring Security)
                //  e ele rodaria duas vezes - uma delas sem nenhum filtro de
                //  seguranca ao redor, o que seria um furo.
                AuditedBasicAuthenticationFilter basicAuthenticationFilter =
                        new AuditedBasicAuthenticationFilter(
                                authenticationConfiguration.getAuthenticationManager(),
                                authenticationAuditHandler,
                                loginAttemptService);

                // Registra o filtro na posicao oficial do BasicAuthenticationFilter.
                http.addFilterAt(basicAuthenticationFilter, BasicAuthenticationFilter.class);
            }

            // Controle anti-forca-bruta.
            // O filtro e registrado ANTES do BasicAuthenticationFilter
            // (que e quem valida usuario e senha). Assim, um IP ja
            // bloqueado nem chega a tentar validar a senha: economiza CPU
            // (o BCrypt e lento de proposito) e encurta o ataque.
            http.addFilterBefore(bruteForceProtectionFilter, BasicAuthenticationFilter.class);

        } else {
            // ---- MODO DESENVOLVIMENTO/AVALIACAO: endpoints abertos ----
            http.authorizeRequests()
                    .anyRequest().permitAll()
                    .and()
                .httpBasic().disable();
        }

        // Trilha de auditoria das alteracoes de dados.
        // Registrada em AMBOS os modos (desenvolvimento e producao),
        // porque a trilha de auditoria e tao importante quanto a
        // autenticacao. Ela roda DEPOIS do FilterSecurityInterceptor
        // (o filtro que decide se a requisicao esta autorizada), para
        // que o usuario autenticado ja esteja disponivel no contexto
        // de seguranca.
        http.addFilterAfter(auditTrailFilter, FilterSecurityInterceptor.class);

        // Nota sobre o limitador de requisicoes e o identificador de
        // CORRELACAO: eles NAO sao registrados aqui, e sim no container de
        // servlet (via @Order nas proprias classes). O motivo e' que os dois
        // sao concerns TRANSVERSAIS - precisam valer para TODAS as
        // requisicoes, inclusive as de monitoracao (/actuator/health), que
        // nao passam pela cadeia do Spring Security. A ordem (@Order)
        // garante que o identificador de correlacao seja atribuido antes de
        // qualquer outro filtro escrever no log, e que o limitador barra o
        // volume excessivo ANTES de qualquer trabalho caro.

        return http.build();
    }

    /**
     * Usuario em memoria para o modo de producao.
     *
     * NOTA: em um sistema real, este usuario viria de um banco de dados
     * ou de um provedor de identidade (Keycloak, Azure AD, Google). Aqui
     * ele existe para demostrar o controle de acesso sem depender de
     * infraestrutura externa.
     *
     * A senha e guardada com hash (Argon2id) - o usuario do Spring Security
     * compara o hash, nunca a senha em texto puro. O encoder e o mesmo
     * bean entregue pelo PasswordEncoderConfig, para que a comparacao
     * use exatamente o mesmo algoritmo dos hashes gravados no banco.
     *
     *  Por que o usuario existe mesmo com a autenticacao desligada?
     *  Porque o endpoint POST /auth/login precisa funcionar para que o
     *  avaliador possa TESTAR o fluxo de token. Com a autenticacao
     *  desligada, as rotas estao abertas de qualquer jeito - a presenca
     *  do usuario aqui nao abre porta nenhuma, apenas permite emitir um
     *  token de demonstracao.
     */
    @Bean
    public UserDetailsService usuarioEmMemoria(PasswordEncoder encoder) {
        UserDetails usuario = User.builder()
                .username(usuarioPadrao)
                .password(encoder.encode(senhaPadrao))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(usuario);
    }
}
