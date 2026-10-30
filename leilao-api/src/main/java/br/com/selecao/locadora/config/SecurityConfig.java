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
     * autenticacao. Registrado antes do filtro de autenticacao basica -
     * veja o metodo securityFilterChain.
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
     * Serve pra obter o AuthenticationManager ja montado (que sabe
     * consultar o UserDetailsService e conferir a senha com o
     * PasswordEncoder). E a forma oficial de pegar esse objeto sem
     * montar o ProviderManager na mao.
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
                    // Spring: a API nunca precisa aparecer dentro de um
                    // iframe, entao a proibicao total e a mais segura.
                    .frameOptions().deny()
                    // Content-Type Options: impede que o navegador "adivinhe" o tipo.
                    .contentTypeOptions().and()
                    // HSTS: obriga o navegador a usar HTTPS nas proximas requisicoes.
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            );

        if (autenticacaoAtiva) {

            // Producao: tudo exige autenticacao.
            http.authorizeRequests()
                    // O health check nao pode exigir credencial: o
                    // orquestrador (Docker/Kubernetes) consulta esse
                    // endpoint e, se ele devolvesse 401, o container seria
                    // considerado morto.
                    .antMatchers("/actuator/health", "/actuator/info").permitAll()
                    // Os endpoints de autenticacao precisam ser publicos,
                    // senao nao haveria como obter o token.
                    .antMatchers("/auth/login", "/auth/refresh",
                                 "/autenticacao/login", "/autenticacao/refresh").permitAll()
                    .anyRequest().authenticated()
                    .and()
                // "acesso negado" sempre em JSON, no mesmo formato do
                // GlobalExceptionHandler (o padrao do Spring seria uma
                // pagina HTML, que quebraria o frontend).
                .exceptionHandling()
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint);

            if ("jwt".equalsIgnoreCase(modoSeguranca)) {

                // Modo JWT. O filtro entra antes do filtro de autenticacao
                // basica: assim uma requisicao com "Authorization: Bearer" e
                // autenticada pelo token, e uma com "Authorization: Basic"
                // cai no filtro basico. Quem chegar sem cabecalho e barrado
                // pelo FilterSecurityInterceptor, com 401 em JSON.
                http.addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class);

            } else {

                // Modo HTTP basic.
                //
                // Por que nao usamos o http.httpBasic()? Porque o
                // HttpBasicConfigurer do Spring Security 5.7 expoe so tres
                // metodos: realmName(), authenticationEntryPoint() e
                // authenticationDetailsSource(). Nao existe um
                // "failureHandler()" - o handler de falha e definido dentro
                // do construtor do BasicAuthenticationFilter e nao da pra
                // trocar pela configuracao.
                //
                // Como o handler padrao devolve uma pagina HTML (e nao o
                // JSON que o frontend espera), usamos o
                // AuditedBasicAuthenticationFilter, que estende o filtro
                // original e sobrescreve o tratamento de sucesso e falha.
                //
                // O filtro e criado aqui, dentro do metodo, e nao
                // declarado como @Bean. Se fosse um @Bean do tipo Filter, o
                // Spring Boot registraria ele tambem como filtro do
                // container de servlet (fora da cadeia do Spring Security)
                // e ele rodaria duas vezes - uma delas sem nenhum filtro de
                // seguranca ao redor, o que seria um furo.
                AuditedBasicAuthenticationFilter basicAuthenticationFilter =
                        new AuditedBasicAuthenticationFilter(
                                authenticationConfiguration.getAuthenticationManager(),
                                authenticationAuditHandler,
                                loginAttemptService);

                // Registra o filtro na posicao oficial do BasicAuthenticationFilter.
                http.addFilterAt(basicAuthenticationFilter, BasicAuthenticationFilter.class);
            }

            // Controle anti-forca-bruta. Fica antes do
            // BasicAuthenticationFilter (que e quem valida usuario e senha),
            // assim um IP ja bloqueado nem chega a tentar validar a senha.
            // Economiza CPU (o hash e lento de proposito) e encurta o ataque.
            http.addFilterBefore(bruteForceProtectionFilter, BasicAuthenticationFilter.class);

        } else {
            // Desenvolvimento/avaliacao: endpoints abertos.
            http.authorizeRequests()
                    .anyRequest().permitAll()
                    .and()
                .httpBasic().disable();
        }

        // Trilha de auditoria das alteracoes de dados. Registrada nos dois
        // modos, porque a trilha importa tanto quanto a autenticacao. Roda
        // depois do FilterSecurityInterceptor (o filtro que decide se a
        // requisicao esta autorizada), pra que o usuario autenticado ja
        // esteja no contexto de seguranca.
        http.addFilterAfter(auditTrailFilter, FilterSecurityInterceptor.class);

        // O limitador de requisicoes e o identificador de correlacao nao
        // sao registrados aqui, e sim no container de servlet (via @Order
        // nas proprias classes). Sao concerns transversais: precisam valer
        // pra todas as requisicoes, inclusive as de monitoracao
        // (/actuator/health), que nao passam pela cadeia do Spring
        // Security. A ordem (@Order) garante que o identificador de
        // correlacao seja atribuido antes de qualquer outro filtro
        // escrever no log, e que o limitador barra o volume excessivo
        // antes de qualquer trabalho caro.

        return http.build();
    }

    /**
     * Usuario em memoria para o modo de producao.
     *
     * Num sistema real este usuario viria de um banco de dados ou de um
     * provedor de identidade (Keycloak, Azure AD, Google). Aqui ele
     * existe pra demonstrar o controle de acesso sem depender de
     * infraestrutura externa.
     *
     * A senha e guardada com hash (Argon2id) - o Spring Security compara
     * o hash, nunca a senha em texto puro. O encoder e o mesmo bean
     * entregue pelo PasswordEncoderConfig, pra comparacao usar
     * exatamente o mesmo algoritmo dos hashes gravados no banco.
     *
     * O usuario existe mesmo com a autenticacao desligada porque o
     * endpoint POST /auth/login precisa funcionar, para dar pra testar o
     * fluxo de token. Com a autenticacao desligada as rotas estao abertas
     * de qualquer jeito: a presenca do usuario aqui nao abre porta
     * nenhuma, so permite emitir um token de demonstracao.
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
