package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

/**
 * ============================================================================
 *  CONFIGURACAO DE SEGURANCA DA API
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  A IDEIA CENTRAL
 *  ------------------------------------------------------
 *  A autenticacao esta LIGADA/DESLIGADA por CONFIGURACAO, e nao por
 *  codigo. Assim o mesmo sistema serve a dois cenarios:
 *
 *   - DESENVOLVIMENTO / AVALIACAO (padrao): os endpoints ficam abertos,
 *     exatamente como o enunciado do teste pede ("GET /unidades" precisa
 *     responder sem login para o avaliador testar rapidinho).
 *
 *   - PRODUCAO (perfil "prod" / variavel SECURITY_ENABLED=true): TODA rota
 *     exige usuario e senha. E o mesmo codigo, sem if espalhado pelo
 *     projeto. Isso se chama "seguranca por configuracao" e e muito mais
 *     seguro do que confiar que ninguem esqueceu de proteger um endpoint.
 *
 *  ------------------------------------------------------
 *  POR QUE DESLIGAR O CSRF?
 *  ------------------------------------------------------
 *  CSRF (Cross-Site Request Forgery) e um ataque em que o navegador
 *  envia, sem querer, uma requisicao para outro site usando a sessao
 *  ativa da pessoa. A defesa classica (token CSRF) existe porque
 *  navegadores anexam cookies automaticamente a requisicoes de sites.
 *
 *  Nesta API nao ha cookie de sessao: a autenticacao e por cabecalho
 *  (HTTP Basic), que o navegador so envia se o codigo pedir. Sem cookie
 *  automatico, o ataque nao se aplica - e o que a propria documentacao
 *  do Spring Security recomenda para APIs stateless.
 *
 *  ------------------------------------------------------
 *  POR QUE "STATELESS"?
 *  ------------------------------------------------------
 *  SessionCreationPolicy.STATELESS diz que o servidor nao cria sessao
 *  no servidor. Isso deixa a API mais rapida (nada de ir ao banco/redis
 *  para checar a sessao), mais simples de escalar (varias instancias do
 *  servidor working sem compartilhar estado) e mais segura (menos estado
 *  acumulado = menos superficie de ataque).
 * ============================================================================
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

    /** Usuario da autenticacao HTTP Basic (perfil de producao). */
    @Value("${app.security.username:admin}")
    private String usuarioPadrao;

    /** Senha da autenticacao HTTP Basic (perfil de producao). */
    @Value("${app.security.password:troque-esta-senha}")
    private String senhaPadrao;

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
            // ---- MODO PRODUCAO: tudo exige usuario e senha ----
            http.authorizeRequests()
                    .antMatchers("/actuator/health", "/actuator/info").permitAll()
                    .anyRequest().authenticated()
                    .and()
                .httpBasic();
        } else {
            // ---- MODO DESENVOLVIMENTO/AVALIACAO: endpoints abertos ----
            http.authorizeRequests()
                    .anyRequest().permitAll()
                    .and()
                .httpBasic().disable();
        }

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
     * A senha e guardada com hash BCrypt - o usuario do Spring Security
     * compara o hash, nunca a senha em texto puro.
     */
    @Bean
    public UserDetailsService usuarioEmMemoria() {
        if (!autenticacaoAtiva) {
            return new InMemoryUserDetailsManager();
        }
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        UserDetails usuario = User.builder()
                .username(usuarioPadrao)
                .password(encoder.encode(senhaPadrao))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(usuario);
    }
}
