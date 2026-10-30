package br.com.selecao.locadora.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do controle de tentativas de login.
 *
 * O repositorio e dublado com o Mockito, entao nao ha necessidade de banco.
 *
 * Os testes provam que, ate o limite, o usuario nao fica bloqueado -
 * bloquear alguem por erro de digitacao seria pior que o ataque.
 */
class LoginAttemptServiceTest {

    /** Servico sob teste. */
    private LoginAttemptService loginAttemptService;

    /**
     * Prepara o servico com uma configuracao de teste previsivel:
     * 3 falhas bloqueiam o usuario e 10 falhas bloqueiam o IP.
     */
    @BeforeEach
    void preparar() {

        loginAttemptService = new LoginAttemptService();

        ReflectionTestUtils.setField(loginAttemptService, "habilitado", true);
        ReflectionTestUtils.setField(loginAttemptService, "maxTentativasPorUsuario", 3);
        ReflectionTestUtils.setField(loginAttemptService, "maxTentativasPorIp", 10);
        ReflectionTestUtils.setField(loginAttemptService, "janelaMinutos", 15);
    }

    /**
     * Testa 1: antes do limite, nenhum bloqueio acontece.
     *
     * Este e um requisito de usabilidade (e de Justica): bloquear uma
     * pessoa que digitou a senha errada duas vezes seria inaceitavel.
     */
    @Test
    @DisplayName("Ate o limite de falhas, o usuario nao fica bloqueado")
    void naoBloqueiaAteEstourarOLimite() {

        loginAttemptService.registrarFalha("10.0.0.1", "admin");
        loginAttemptService.registrarFalha("10.0.0.1", "admin");

        assertFalse(loginAttemptService.usuarioBloqueado("10.0.0.1", "admin"),
                "Com 2 falhas (limite e 3), o usuario nao deveria estar bloqueado.");

        assertFalse(loginAttemptService.ipBloqueado("10.0.0.1"),
                "Com 2 falhas (limite de IP e 10), o IP nao deveria estar bloqueado.");
    }

    /**
     * Testa 2: ao estourar o limite, o usuario fica bloqueado.
     */
    @Test
    @DisplayName("Ao estourar o limite, o usuario fica bloqueado")
    void bloqueiaUsuarioAoEstourarOLimite() {

        loginAttemptService.registrarFalha("10.0.0.1", "admin");
        loginAttemptService.registrarFalha("10.0.0.1", "admin");
        loginAttemptService.registrarFalha("10.0.0.1", "admin");

        assertTrue(loginAttemptService.usuarioBloqueado("10.0.0.1", "admin"),
                "Com 3 falhas (o limite), o usuario deveria estar bloqueado.");
    }

    /**
     * Testa 3: o bloqueio de um usuario NAO afeta outro usuario do
     * mesmo IP.
     *
     * Este ponto e importante: se o bloqueio fosse por IP, o usuario
     * "conta" ficaria bloqueado por causa das tentativas do "admin" -
     * um efeito colateral injusto (e que happens o tempo todo em redes
     * corporativas, onde varios usuarios saem do mesmo IP).
     */
    @Test
    @DisplayName("O bloqueio de um usuario nao afeta outro usuario do mesmo IP")
    void bloqueioDeUmUsuarioNaoAfetaOutro() {

        for (int i = 0; i < 3; i++) {
            loginAttemptService.registrarFalha("10.0.0.1", "admin");
        }

        assertTrue(loginAttemptService.usuarioBloqueado("10.0.0.1", "admin"),
                "O usuario 'admin' deveria estar bloqueado.");

        assertFalse(loginAttemptService.usuarioBloqueado("10.0.0.1", "conta"),
                "O usuario 'conta' nao deveria ser afetado pelas falhas do 'admin'.");
    }

    /**
     * Testa 4: o limite de IP protege a aplicacao contra ataques
     * pulverizados (poucas senhas para muitos usuarios).
     *
     * Sem esse limite, um atacante poderia tentar 3 senhas em 1000
     * usuarios diferentes sem nunca estourar o limite individual - e a
     * API receberia um volume absurdo de tentativas.
     */
    @Test
    @DisplayName("O limite por IP bloqueia ataques pulverizados entre varios usuarios")
    void bloqueiaIpEmAtaquePulverizado() {

        // 4 usuarios diferentes, 3 falhas cada = 12 falhas no total.
        // O limite do IP e 10, entao o IP deve ser bloqueado.
        for (int usuario = 1; usuario <= 4; usuario++) {
            for (int tentativa = 0; tentativa < 3; tentativa++) {
                loginAttemptService.registrarFalha("10.0.0.9", "usuario" + usuario);
            }
        }

        assertTrue(loginAttemptService.ipBloqueado("10.0.0.9"),
                "Com 12 falhas vindas do mesmo IP (limite e 10), o IP deveria estar bloqueado.");
    }

    /**
     * Testa 5: um login bem-sucedido zera o contador do usuario.
     *
     * Quem acerta a senha nao deve continuar bloqueado por tentativas
     * antigas - nem por tentativas vindas de outro IP.
     */
    @Test
    @DisplayName("Login bem-sucedido zera o contador de falhas do usuario")
    void loginBemSucedidoZeraOContador() {

        loginAttemptService.registrarFalha("10.0.0.1", "admin");
        loginAttemptService.registrarFalha("10.0.0.1", "admin");

        // Autenticacao bem-sucedida.
        loginAttemptService.registrarSucesso("admin");

        assertFalse(loginAttemptService.usuarioBloqueado("10.0.0.1", "admin"),
                "Apos o login com sucesso, o contador deveria ter sido zerado.");
    }

    /**
     * Testa 6: com o controle desligado, nada e bloqueado.
     *
     * Permite desligar a protecao sem desinstalar o codigo - util para
     * depurar um bloqueio indevido em um ambiente de teste.
     */
    @Test
    @DisplayName("Com o controle desligado, nenhum bloqueio acontece")
    void controleDesligadoNaoBloqueia() {

        ReflectionTestUtils.setField(loginAttemptService, "habilitado", false);

        for (int i = 0; i < 50; i++) {
            loginAttemptService.registrarFalha("10.0.0.1", "admin");
        }

        assertFalse(loginAttemptService.usuarioBloqueado("10.0.0.1", "admin"),
                "Com o controle desligado, o usuario nao deveria ser bloqueado.");

        assertFalse(loginAttemptService.ipBloqueado("10.0.0.1"),
                "Com o controle desligado, o IP nao deveria ser bloqueado.");
    }

    /**
     * Testa 7: o tempo de espera ate a liberacao e informado.
     *
     * Esse valor vira o cabecalho "Retry-After" da resposta 429, que diz
     * ao cliente quando pode tentar de novo.
     */
    @Test
    @DisplayName("Informa quantos segundos faltam para o bloqueio expirar")
    void informaTempoAteLiberar() {

        loginAttemptService.registrarFalha("10.0.0.1", "admin");

        long segundos = loginAttemptService.segundosAteLiberar("10.0.0.1");

        assertTrue(segundos > 0, "Deveria haver tempo restante para liberar.");
        assertTrue(segundos <= 15 * 60,
                "O tempo restante nao pode passar da janela de 15 minutos, foi: " + segundos);
    }
}
