package br.com.selecao.locadora.business;

import br.com.selecao.locadora.entity.Auditoria;
import br.com.selecao.locadora.repository.AuditoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes da trilha de auditoria.
 *
 * O repositorio e dublado com o Mockito: o que esta em teste e a logica do
 * selo (a corrente de hashes), nao o SQL.
 *
 * Os testes provam que a corrente se forma, que ela verifica como integra, e
 * que alterar, forjar ou remover um registro e detectado com a sequencia da
 * ruptura.
 */
class AuditoriaBOTest {

    /**
     * Chave de teste. Tem 32 bytes (256 bits) - o mesmo tamanho minimo
     * exigido em producao.
     */
    private static final String CHAVE_DE_TESTE = "chave-de-teste-com-32-bytes-de-verdade!";

    /**
     * Negocio sob teste.
     */
    private AuditoriaBO auditoriaBO;

    /**
     * Repositorio dublado.
     */
    private AuditoriaRepository repositorio;

    /**
     * Lista dos registros "gravados", que simula a tabela.
     */
    private List<Auditoria> tabela;

    @BeforeEach
    void preparar() {

        repositorio = mock(AuditoriaRepository.class);
        tabela = new ArrayList<>();

        auditoriaBO = new AuditoriaBO();

        // Injeta o repositorio e a chave pelo mesmo caminho que o Spring
        // usaria (o teste nao sobe o container).
        ReflectionTestUtils.setField(auditoriaBO, "auditoriaRepository", repositorio);
        ReflectionTestUtils.setField(auditoriaBO, "chaveConfigurada", CHAVE_DE_TESTE);
        auditoriaBO.inicializar();

        // O repositorio dublado devolve o ultimo registro da "tabela".
        when(repositorio.findTopByOrderBySequenciaDesc()).thenAnswer(invocacao -> {

            if (tabela.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(tabela.get(tabela.size() - 1));
        });

        when(repositorio.obterMaiorSequencia()).thenAnswer(invocacao -> {

            if (tabela.isEmpty()) {
                return 0L;
            }

            return tabela.get(tabela.size() - 1).getSequencia();
        });

        when(repositorio.findBySequenciaGreaterThanEqualOrderBySequenciaAsc(any(Long.class)))
                .thenAnswer(invocacao -> new ArrayList<>(tabela));

        // Gravar = acrescentar na "tabela".
        doAnswer(invocacao -> {
            tabela.add(invocacao.getArgument(0));
            return invocacao.getArgument(0);
        }).when(repositorio).save(any(Auditoria.class));
    }

    /**
     * Teste 1: a cadeia se forma - cada registro guarda o hash do anterior.
     */
    @Test
    @DisplayName("A cadeia se forma: cada registro guarda o hash do anterior")
    void cadeiaSeForma() {

        registrar("admin", "POST", "/empresas", 201);
        registrar("admin", "POST", "/leiloes", 201);
        registrar("admin", "PUT", "/leiloes/1", 200);

        assertTrue(tabela.size() == 3, "devem existir 3 registros");

        // O primeiro nao tem anterior (e' o inicio da cadeia).
        assertTrue(tabela.get(0).getHashAnterior() == null,
                "o primeiro registro nao tem hash anterior");

        // Os demais apontam para o hash do registro que vem antes.
        assertTrue(tabela.get(0).getHashAtual().equals(tabela.get(1).getHashAnterior()),
                "o registro 2 deve encadear no hash do registro 1");
        assertTrue(tabela.get(1).getHashAtual().equals(tabela.get(2).getHashAnterior()),
                "o registro 3 deve encadear no hash do registro 2");

        // E os hashes sao todos diferentes (o conteudo entrou no selo).
        assertFalse(tabela.get(0).getHashAtual().equals(tabela.get(1).getHashAtual()),
                "registros diferentes precisam ter selos diferentes");
    }

    /**
     * Teste 2: a cadeia integra e verificada como integra.
     */
    @Test
    @DisplayName("Uma cadeia intacta e verificada como integra")
    void cadeiaIntactaEVerificadaComoIntegra() {

        registrar("admin", "POST", "/empresas", 201);
        registrar("admin", "POST", "/leiloes", 201);

        AuditoriaBO.ResultadoVerificacao resultado = auditoriaBO.verificar(1);

        assertTrue(resultado.isIntegra(), "a cadeia intacta deve ser considerada integra");
        assertTrue(resultado.getVerificados() == 2, "os 2 registros devem ter sido verificados");
        assertNotNull(resultado.getMensagem());
    }

    /**
     * Teste 3: ALTERAR o conteudo de um registro e detectado.
     *
     * Este e' o cenario central: um atacante com acesso ao banco muda o
     * registro para esconder o que fez. A verificacao tem de acusar, e
     * dizer ONDE.
     */
    @Test
    @DisplayName("Adulterar o conteudo de um registro e detectado")
    void adulteracaoDeConteudoEDetectada() {

        registrar("admin", "POST", "/empresas", 201);
        registrar("admin", "POST", "/leiloes", 201);
        registrar("admin", "DELETE", "/leiloes/1", 204);

        // O atacante muda o conteudo do registro 2, direto no banco.
        Auditoria adulterado = tabela.get(1);
        adulterado.setCaminho("/usuarios/1");
        adulterado.setAcao("EXCLUIR USUARIO");

        AuditoriaBO.ResultadoVerificacao resultado = auditoriaBO.verificar(1);

        assertFalse(resultado.isIntegra(), "a adulteracao DEVE ser detectada");
        assertTrue(resultado.getSequenciaRompida() != null
                        && resultado.getSequenciaRompida() == 2L,
                "a verificacao deve apontar a sequencia 2 como a rompida");
        assertTrue(resultado.getMensagem().contains("adulterado"),
                "a mensagem deve dizer que o registro foi adulterado");
    }

    /**
     * Teste 4: QUEBRAR o encadeamento e detectado.
     *
     * Aqui o atacante nem muda o conteudo: ele troca o "hash_anterior" para
     * tentar criar um registro novo, colado na cadeia, com conteudo
     * livre. O selo do conteudo nao bate, e a adulteracao e detectada.
     */
    @Test
    @DisplayName("Quebrar o encadeamento dos hashes e detectado")
    void encadeamentoQuebradoEDetectado() {

        registrar("admin", "POST", "/empresas", 201);
        registrar("admin", "POST", "/leiloes", 201);

        // O atacante forja um encadeamento no registro 2.
        tabela.get(1).setHashAnterior("0000000000000000000000000000000000000000000000000000000000000000");

        AuditoriaBO.ResultadoVerificacao resultado = auditoriaBO.verificar(1);

        assertFalse(resultado.isIntegra(), "o encadeamento quebrado DEVE ser detectado");
        assertTrue(resultado.getSequenciaRompida() == 2L,
                "a verificacao deve apontar a sequencia 2");
    }

    /**
     * Teste 5: REMOVER um registro do meio e detectado.
     *
     * O atacante apaga o registro 2 do meio da cadeia. O encadeamento
     * quebra (o registro 3 aponta para um hash que nao existe mais na
     * tabela) - e a verificacao acusa.
     */
    @Test
    @DisplayName("Remover um registro do meio e detectado")
    void remocaoDeRegistroEDetectada() {

        registrar("admin", "POST", "/empresas", 201);
        registrar("admin", "POST", "/leiloes", 201);
        registrar("admin", "DELETE", "/leiloes/1", 204);

        // O atacante apaga o registro do meio.
        tabela.remove(1);

        AuditoriaBO.ResultadoVerificacao resultado = auditoriaBO.verificar(1);

        assertFalse(resultado.isIntegra(), "a remocao DEVE ser detectada");
    }

    /**
     * Teste 6: a chave e OBRIGATORIA.
     *
     * Sem chave, qualquer um que tenha acesso ao banco poderia forjar um
     * registro valido. Por isso a chave tem tamanho minimo e a aplicacao
     * RECUSA subir sem ela valida.
     */
    @Test
    @DisplayName("A chave HMAC curta demais e recusada")
    void chaveCurtaEDeRecusada() {

        AuditoriaBO outro = new AuditoriaBO();
        ReflectionTestUtils.setField(outro, "chaveConfigurada", "curta");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, outro::inicializar,
                "uma chave curta demais deve ser recusada na inicializacao");
    }

    /**
     * Registra um evento na trilha.
     */
    private void registrar(String usuario, String metodo, String caminho, int status) {
        auditoriaBO.registrar(usuario, "127.0.0.1", metodo, caminho, status, 12L,
                metodo + " " + caminho, null);
    }
}
