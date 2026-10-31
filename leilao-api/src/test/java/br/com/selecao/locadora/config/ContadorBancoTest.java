package br.com.selecao.locadora.config;

import br.com.selecao.locadora.entity.ContadorSeguranca;
import br.com.selecao.locadora.repository.ContadorSegurancaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes do contador guardado no banco.
 *
 * O motivo de existir: o contador em memoria vale so para a instancia
 * que o guarda. Com varias replicas da API atras do balanceador, o
 * limite por IP passa a valer por replica - e o atacante pode
 * distribuir as tentativas para furar o limite.
 *
 * Aqui o repositorio e' um duble que reproduz a semantica do SQL nativo
 * (o "ON CONFLICT" que renova a janela e reseta a contagem quando ela
 * venceu). O que se verifica e' a logica do BO em cima do banco: a
 * janela renovada, o resete e a limpeza por prefixo.
 *
 * O SQL nativo em si so pode ser conferido contra um PostgreSQL de
 * verdade - e nao ha um no CI.
 */
class ContadorBancoTest {

    private static final String CHAVE = "requisicao:10.0.0.1";

    private ContadorBanco contadorBanco;

    /**
     * O duble do repositorio, com o estado dos contadores.
     */
    private Map<String, ContadorSeguranca> dados;

    @BeforeEach
    void preparar() {

        dados = new HashMap<>();

        ContadorSegurancaRepository repository = mock(ContadorSegurancaRepository.class);

        // Reproduz o "INSERT ... ON CONFLICT DO UPDATE" do repositorio.
        when(repository.incrementar(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocacao -> {

                    String chave = invocacao.getArgument(0);
                    LocalDateTime agora = invocacao.getArgument(1);
                    LocalDateTime expira = invocacao.getArgument(2);

                    ContadorSeguranca existente = dados.get(chave);

                    if (existente == null) {
                        ContadorSeguranca novo = new ContadorSeguranca();
                        novo.setChave(chave);
                        novo.setContador(1);
                        novo.setExpiraEm(expira);
                        dados.put(chave, novo);
                        return 1;
                    }

                    if (existente.getExpiraEm().isBefore(agora)) {
                        existente.setContador(1);
                    } else {
                        existente.setContador(existente.getContador() + 1);
                    }

                    existente.setExpiraEm(expira);
                    return 1;
                });

        when(repository.findById(anyString()))
                .thenAnswer(invocacao -> Optional.ofNullable(dados.get(invocacao.getArgument(0))));

        when(repository.removerPorPrefixo(anyString()))
                .thenAnswer(invocacao -> {
                    String prefixo = invocacao.getArgument(0);
                    int removidos = 0;
                    for (String chave : new java.util.ArrayList<>(dados.keySet())) {
                        if (chave.startsWith(prefixo) && dados.remove(chave) != null) {
                            removidos++;
                        }
                    }
                    return removidos;
                });

        when(repository.removerExpirados(any(LocalDateTime.class)))
                .thenAnswer(invocacao -> {
                    LocalDateTime limite = invocacao.getArgument(0);
                    int removidos = 0;
                    for (Map.Entry<String, ContadorSeguranca> entrada
                            : new java.util.ArrayList<>(dados.entrySet())) {
                        if (entrada.getValue().getExpiraEm().isBefore(limite)
                                && dados.remove(entrada.getKey()) != null) {
                            removidos++;
                        }
                    }
                    return removidos;
                });

        contadorBanco = new ContadorBanco();
        ReflectionTestUtils.setField(contadorBanco, "contadorSegurancaRepository", repository);
    }

    /**
     * O contador cresce a cada incremento.
     */
    @Test
    @DisplayName("O contador cresce a cada incremento")
    void oContadorCresceACadaIncremento() {

        assertEquals(1, contadorBanco.incrementar(CHAVE, Duration.ofMinutes(1)).getQuantidade());
        assertEquals(2, contadorBanco.incrementar(CHAVE, Duration.ofMinutes(1)).getQuantidade());
        assertEquals(3, contadorBanco.incrementar(CHAVE, Duration.ofMinutes(1)).getQuantidade());
    }

    /**
     * A consulta devolve a contagem atual sem alterar nada.
     */
    @Test
    @DisplayName("A consulta devolve a contagem sem alterar nada")
    void aConsultaDevolveAContagemSemAlterar() {

        contadorBanco.incrementar(CHAVE, Duration.ofMinutes(1));
        contadorBanco.incrementar(CHAVE, Duration.ofMinutes(1));

        assertEquals(2, contadorBanco.consultar(CHAVE).getQuantidade());

        // Consultar de novo nao pode aumentar o contador.
        assertEquals(2, contadorBanco.consultar(CHAVE).getQuantidade());
    }

    /**
     * Chave que nao existe devolve contagem zerada.
     */
    @Test
    @DisplayName("Chave que nao existe devolve contagem zerada")
    void chaveInexistenteDevolveZero() {

        assertEquals(0, contadorBanco.consultar("requisicao:inexistente").getQuantidade());
    }

    /**
     * O registro com a janela vencida nao conta.
     *
     * E o que faz o limite "deslizante" funcionar: passado o prazo sem
     * nova ocorrencia, a contagem recomeca.
     */
    @Test
    @DisplayName("Registro com a janela vencida nao conta")
    void registroComJanelaVencidaNaoConta() {

        ContadorSeguranca registro = new ContadorSeguranca();
        registro.setChave(CHAVE);
        registro.setContador(50);
        registro.setExpiraEm(LocalDateTime.now().minusMinutes(1));

        dados.put(CHAVE, registro);

        assertEquals(0, contadorBanco.consultar(CHAVE).getQuantidade());
    }

    /**
     * A janela e renovada a cada incremento.
     *
     * E o que permite o ataque continuo: o atacante tem 5 tentativas a
     * cada 15 minutos, e nao 5 no total para sempre.
     */
    @Test
    @DisplayName("A janela e renovada a cada incremento")
    void aJanelaERenovadaACadaIncremento() {

        Contagem primeira = contadorBanco.incrementar(CHAVE, Duration.ofMinutes(15));
        Contagem segunda = contadorBanco.incrementar(CHAVE, Duration.ofMinutes(15));

        assertFalse(segunda.getExpiraEm().isBefore(primeira.getExpiraEm()),
                "A janela renovada nao pode encurtar.");
    }

    /**
     * A remocao por prefixo apaga todos os contadores do usuario,
     * de qualquer IP.
     *
     * E o que zera o contador depois de um login bem-sucedido.
     */
    @Test
    @DisplayName("A remocao por prefixo apaga todos os contadores do usuario")
    void aRemocaoPorPrefixoApagaTodosOsContadoresDoUsuario() {

        contadorBanco.incrementar("tentativa:usuario:admin:ip:10.0.0.1", Duration.ofMinutes(15));
        contadorBanco.incrementar("tentativa:usuario:admin:ip:10.0.0.2", Duration.ofMinutes(15));
        contadorBanco.incrementar("tentativa:usuario:outro:ip:10.0.0.1", Duration.ofMinutes(15));
        contadorBanco.incrementar("tentativa:ip:10.0.0.1", Duration.ofMinutes(15));

        int removidos = contadorBanco.removerPorPrefixo("tentativa:usuario:admin:");

        assertEquals(2, removidos, "Os dois contadores do usuario admin devem sumir.");
        assertTrue(dados.containsKey("tentativa:usuario:outro:ip:10.0.0.1"),
                "O contador de outro usuario nao pode ser apagado.");
        assertTrue(dados.containsKey("tentativa:ip:10.0.0.1"),
                "O contador do IP nao pode ser apagado.");
    }

    /**
     * A limpeza remove os registros vencidos.
     */
    @Test
    @DisplayName("A limpeza remove os registros vencidos")
    void aLimpezaRemoveOsVencidos() {

        contadorBanco.incrementar(CHAVE, Duration.ofMinutes(1));

        // O registro ainda esta valido: nada a remover.
        assertEquals(0, contadorBanco.limparExpirados());
        assertTrue(dados.containsKey(CHAVE));
    }
}
