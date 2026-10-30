package br.com.selecao.locadora.business;

import br.com.selecao.locadora.config.LoginAttemptService;
import br.com.selecao.locadora.config.PasswordEncoderConfig;
import br.com.selecao.locadora.dto.LoginRequest;
import br.com.selecao.locadora.dto.LoginResponse;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import br.com.selecao.locadora.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes do login por JSON (POST /auth/login).
 *
 * O ponto central deste arquivo: o login por JSON e o login por HTTP Basic
 * sao dois caminhos para a mesma porta, e os dois precisam contar a falha
 * no mesmo controle de tentativas.
 *
 * Quando so um dos dois contava, o atacante escolhia o caminho sem
 * controle. Bastava usar POST /auth/login (que emite JWT) em vez do
 * cabecalho Authorization: Basic, e o bloqueio por IP e por usuario
 * simplesmente nao acontecia, porque o contador nunca crescia.
 *
 * Por isso estes testes olham o contador depois de cada tentativa.
 */
class AutenticacaoBOTest {

    private static final String SENHA_CORRETA = "SenhaForteDeTeste123";

    private AutenticacaoBO autenticacaoBO;

    private LoginAttemptService loginAttemptService;

    private PasswordEncoder passwordEncoder;

    /**
     * Monta o BO na mao, sem subir o contexto do Spring.
     *
     * O teste nao precisa de banco: os campos que o BO usa sao
     * injetados por reflexao (o mesmo caminho que o Spring usa), e os
     * servicos de usuario e de token viram dubles. Sem isso, o teste
     * exigiria um PostgreSQL no ar - e a suite inteira perderia a
     * garantia de rodar em qualquer maquina.
     */
    @BeforeEach
    void preparar() throws Exception {

        loginAttemptService = new LoginAttemptService();
        passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

        // Os limites do controle de tentativas vem de @Value, entao
        // precisam ser injetados a mao: sem o Spring, ficariam em false
        // e o controle nao contaria nada.
        injetarNo(loginAttemptService, "habilitado", true);
        injetarNo(loginAttemptService, "maxTentativasPorUsuario", 5);
        injetarNo(loginAttemptService, "maxTentativasPorIp", 20);
        injetarNo(loginAttemptService, "janelaMinutos", 15);

        // O servico de usuarios so conhece o "admin". Qualquer outro
        // nome e' usuario inexistente.
        UserDetailsService userDetailsService = nome -> {
            if ("admin".equals(nome)) {
                return User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode(SENHA_CORRETA))
                        .roles("ADMIN")
                        .build();
            }
            throw new UsernameNotFoundException("nao existe");
        };

        // O emissor de tokens e' duble: aqui o que interessa e' o
        // controle de tentativas, nao a assinatura do token.
        TokenJwtBO tokenJwtBO = mock(TokenJwtBO.class);
        when(tokenJwtBO.emitirTokenAcesso(anyString())).thenReturn("token-de-acesso");
        when(tokenJwtBO.emitirTokenRenovacao(anyString())).thenReturn("token-de-renovacao");
        when(tokenJwtBO.getValidadeTokenAcesso()).thenReturn(Duration.ofMinutes(15));
        when(tokenJwtBO.getValidadeTokenRenovacao()).thenReturn(Duration.ofDays(7));

        autenticacaoBO = new AutenticacaoBO();
        injetar("userDetailsService", userDetailsService);
        injetar("passwordEncoder", passwordEncoder);
        injetar("tokenJwtBO", tokenJwtBO);
        injetar("loginAttemptService", loginAttemptService);
        injetar("refreshTokenRepository", mock(RefreshTokenRepository.class));

        // Prepara o hash de referencia do usuario inexistente, que o BO
        // gera na subida.
        injetar("hashSenhaInexistente", passwordEncoder.encode(UUID.randomUUID().toString()));

        loginAttemptService.limpar();
    }

    /**
     * Injeta um valor num campo privado do BO.
     */
    private void injetar(String campo, Object valor) throws Exception {
        Field campoReflexivo = AutenticacaoBO.class.getDeclaredField(campo);
        campoReflexivo.setAccessible(true);
        campoReflexivo.set(autenticacaoBO, valor);
    }

    /**
     * Injeta um valor num campo privado de qualquer objeto.
     */
    private void injetarNo(Object alvo, String campo, Object valor) throws Exception {
        Field campoReflexivo = alvo.getClass().getDeclaredField(campo);
        campoReflexivo.setAccessible(true);
        campoReflexivo.set(alvo, valor);
    }

    /**
     * Teste 1: uma senha errada conta como tentativa no controle.
     *
     * Sem este registro, o contador nunca cresceria neste caminho e o
     * bloqueio por IP nunca aconteceria no login por JSON.
     */
    @Test
    @DisplayName("Senha errada no login por JSON conta como tentativa de login")
    void senhaErradaContaComoTentativa() {

        LoginRequest request = new LoginRequest();
        request.setUsuario("admin");
        request.setSenha("senha-errada");

        assertThrows(TokenInvalidoException.class,
                () -> autenticacaoBO.autenticar(request, "192.168.1.10"));

        assertTrue(loginAttemptService.contarTentativas("192.168.1.10", "admin") > 0,
                "A falha do login por JSON tem que contar no controle de tentativas. "
                        + "Se nao contar, o atacante usa este caminho para escapar do bloqueio.");
    }

    /**
     * Teste 2: usuario inexistente tambem conta.
     *
     * O atacante nao sabe se o usuario existe (a resposta e a mesma nos
     * dois casos), entao do ponto de vista do controle e uma tentativa
     * igual a qualquer outra.
     */
    @Test
    @DisplayName("Usuario inexistente tambem conta como tentativa")
    void usuarioInexistenteTambemConta() {

        LoginRequest request = new LoginRequest();
        request.setUsuario("usuario-que-nao-existe");
        request.setSenha("qualquer-coisa");

        assertThrows(TokenInvalidoException.class,
                () -> autenticacaoBO.autenticar(request, "192.168.1.11"));

        assertTrue(loginAttemptService.contarTentativas("192.168.1.11", "usuario-que-nao-existe") > 0,
                "Tentativa com usuario inexistente tem que contar no controle de tentativas.");
    }

    /**
     * Teste 3: o contador por IP tambem cresce.
     *
     * O bloqueio por IP e a ultima linha de defesa: mesmo que o
     * atacante troque de usuario, o IP dele continua accumulando.
     */
    @Test
    @DisplayName("O contador por IP tambem cresce no login por JSON")
    void contadorPorIpTambemCresce() {

        LoginRequest request = new LoginRequest();
        request.setUsuario("admin");
        request.setSenha("senha-errada");

        assertThrows(TokenInvalidoException.class,
                () -> autenticacaoBO.autenticar(request, "192.168.1.12"));

        assertTrue(loginAttemptService.contarTentativas("192.168.1.12") > 0,
                "O total por IP tem que crescer, senao o atacante troca de usuario e escapa.");
    }

    /**
     * Teste 4: um login valido zera o contador do usuario.
     *
     * Sem isso, um usuario legitimo que errasse a senha duas vezes
     * ficaria bloqueado na terceira, mesmo depois de acertar.
     */
    @Test
    @DisplayName("Login valido zera o contador do usuario")
    void loginValidoZeraOContador() {

        LoginRequest request = new LoginRequest();
        request.setUsuario("admin");
        request.setSenha("senha-errada");

        // Duas falhas.
        assertThrows(TokenInvalidoException.class,
                () -> autenticacaoBO.autenticar(request, "192.168.1.13"));
        assertThrows(TokenInvalidoException.class,
                () -> autenticacaoBO.autenticar(request, "192.168.1.13"));

        assertTrue(loginAttemptService.contarTentativas("192.168.1.13", "admin") >= 2,
                "Duas falhas deveriam ter acumulado duas tentativas.");

        // Um acerto: o contador do usuario zera.
        LoginRequest bom = new LoginRequest();
        bom.setUsuario("admin");
        bom.setSenha(SENHA_CORRETA);

        LoginResponse resposta = autenticacaoBO.autenticar(bom, "192.168.1.13");

        assertNotNull(resposta, "O login valido devolve o par de tokens.");
        assertEquals(0, loginAttemptService.contarTentativas("192.168.1.13", "admin"),
                "Um login valido tem que zerar o contador do usuario.");
    }

    /**
     * Teste 5: varias falhas deixam o usuario bloqueado.
     *
     * O login por JSON nao pode ser um caminho sem controle: e o mesmo
     * porte que o HTTP Basic, e precisa ter a mesma protecao.
     */
    @Test
    @DisplayName("O usuario fica bloqueado depois de varias tentativas")
    void usuarioFicaBloqueadoAposVariasTentativas() {

        LoginRequest request = new LoginRequest();
        request.setUsuario("admin");
        request.setSenha("senha-errada");

        String ip = "192.168.1.14";

        for (int i = 0; i < 5; i++) {
            assertThrows(TokenInvalidoException.class,
                    () -> autenticacaoBO.autenticar(request, ip));
        }

        assertTrue(loginAttemptService.usuarioBloqueado(ip, "admin"),
                "Depois de varias falhas, o usuario tem que estar bloqueado. "
                        + "O login por JSON nao pode ser um caminho sem controle.");
    }
}
