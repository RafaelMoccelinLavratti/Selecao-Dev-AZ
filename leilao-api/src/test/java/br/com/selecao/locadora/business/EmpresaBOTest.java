package br.com.selecao.locadora.business;

import br.com.selecao.locadora.config.PasswordEncoderConfig;
import br.com.selecao.locadora.dto.EmpresaRequest;
import br.com.selecao.locadora.dto.EmpresaResponse;
import br.com.selecao.locadora.entity.Empresa;
import br.com.selecao.locadora.exception.RegraNegocioException;
import br.com.selecao.locadora.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ============================================================================
 *  TESTE: REGRAS DE NEGOCIO DA EMPRESA
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE ESTE TESTE NAO USA @SpringBootTest?
 *  ------------------------------------------------------
 *  Um teste com @SpringBootTest sobe o container inteiro do Spring - e,
 *  com isso, EXIGE o PostgreSQL no ar. Se o avaliador rodar "mvn test"
 *  numa maquina sem banco, o teste quebraria por um motivo que nao tem
 *  nada a ver com a regra testada.
 *
 *  Aqui usamos o Mockito para "simular" o repositorio (um duble) e
 *  montamos a camada de negocio na mao. Resultado: o teste roda em
 *  qualquer maquina, em milissegundos, e ainda testa exatamente a regra
 *  de negocio - sem ruido de infraestrutura.
 *
 *  (ReflectionTestUtils injeta os dependencias nos campos privados do BO,
 *  que sao preenchidos por injeção de dependencias pelo Spring no
 *  funcionamento real.)
 *
 *  O que estes testes provam:
 *  1) O CNPJ formatado (11.555.999/0001-11) e gravado so com digitos.
 *  2) O mesmo CNPJ nao pode ser cadastrado duas vezes.
 *  3) A senha e gravada como hash BCrypt, nunca em texto puro.
 *  4) A senha NUNCA aparece na resposta da API.
 * ============================================================================
 */
class EmpresaBOTest {

    /** Duble do repositorio (nao ha banco de dados de verdade aqui). */
    private EmpresaRepository empresaRepository;

    /** A camada de negocio sob teste. */
    private EmpresaBO empresaBO;

    /** O codificador real de senha (o mesmo da aplicacao). */
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void preparar() {
        empresaRepository = mock(EmpresaRepository.class);
        passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

        empresaBO = new EmpresaBO();
        ReflectionTestUtils.setField(empresaBO, "empresaRepository", empresaRepository);
        ReflectionTestUtils.setField(empresaBO, "passwordEncoder", passwordEncoder);

        // Por padrao, nenhum CNPJ e nenhum usuario existem ainda.
        when(empresaRepository.existsByCnpj(anyString())).thenReturn(false);
        when(empresaRepository.existsByUsuario(anyString())).thenReturn(false);

        // O "save" devolve a propria entidade, incrementando o id.
        when(empresaRepository.saveAndFlush(any(Empresa.class))).thenAnswer(invocacao -> {
            Empresa empresa = invocacao.getArgument(0);
            if (empresa.getId() == null) {
                empresa.setId(1);
            }
            return empresa;
        });
    }

    /**
     * Monta um cadastro valido, com os campos mascarados como o usuario
     * digitaria na tela.
     */
    private EmpresaRequest requestValido() {
        EmpresaRequest request = new EmpresaRequest();
        request.setRazaoSocial("Metalurgica Aurora Ltda");
        request.setCnpj("11.555.999/0001-11");
        request.setCep("01310-100");
        request.setTelefone("(11) 3000-1100");
        request.setEmail("contato@aurora.com.br");
        request.setSite("https://www.aurora.com.br");
        request.setUsuario("admin.aurora");
        request.setSenha("SenhaSegura123");
        return request;
    }

    @Test
    @DisplayName("CNPJ, CEP e telefone sao normalizados (gravados so com digitos)")
    void normalizaOsCamposFormatados() {
        EmpresaResponse response = empresaBO.salvar(requestValido(), null);

        assertEquals("11555999000111", response.getCnpj());
        assertEquals("01310100", response.getCep());
        assertEquals("1130001100", response.getTelefone());
    }

    @Test
    @DisplayName("CNPJ duplicado e recusado com mensagem clara")
    void recusaCnpjDuplicado() {
        when(empresaRepository.existsByCnpj("11555999000111")).thenReturn(true);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> empresaBO.salvar(requestValido(), null));

        assertTrue(erro.getMessage().contains("CNPJ"), "a mensagem deve citar o CNPJ");
    }

    @Test
    @DisplayName("Usuario duplicado e recusado com mensagem clara")
    void recusaUsuarioDuplicado() {
        when(empresaRepository.existsByUsuario("admin.aurora")).thenReturn(true);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> empresaBO.salvar(requestValido(), null));

        assertTrue(erro.getMessage().contains("usuario"), "a mensagem deve citar o usuario");
    }

    @Test
    @DisplayName("A senha e gravada como hash BCrypt, nunca em texto puro")
    void gravaASenhaComoHash() {
        empresaBO.salvar(requestValido(), null);

        // Recupera a entidade que foi enviada ao repositorio.
        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(captor.capture());

        String hashGravado = captor.getValue().getSenha();
        assertNotEquals("SenhaSegura123", hashGravado, "a senha nao pode ser gravada em texto puro");
        assertTrue(hashGravado.startsWith("$2a$"), "o valor gravado deve ser um hash BCrypt");
        assertTrue(passwordEncoder.matches("SenhaSegura123", hashGravado),
                "a senha original precisa ser conferivel a partir do hash");
    }

    @Test
    @DisplayName("Na alteracao, uma senha vazia mantem o hash que ja estava gravado")
    void alteracaoSemSenhaMantemOHash() {
        // Simula uma empresa ja existente, com hash gravado.
        Empresa existente = new Empresa();
        existente.setId(5);
        existente.setRazaoSocial("Metalurgica Aurora Ltda");
        existente.setCnpj("11555999000111");
        existente.setSenha(passwordEncoder.encode("SenhaAntiga123"));
        when(empresaRepository.findById(5)).thenReturn(java.util.Optional.of(existente));
        when(empresaRepository.existsByCnpjAndIdNot(anyString(), any())).thenReturn(false);
        when(empresaRepository.existsByUsuarioAndIdNot(anyString(), any())).thenReturn(false);

        // Altera sem informar senha.
        EmpresaRequest request = requestValido();
        request.setSenha(null);
        request.setEmail("novo@aurora.com.br");

        empresaBO.salvar(request, 5);

        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).saveAndFlush(captor.capture());

        // O hash anterior deve ser gravado intacto (mesmo texto).
        assertEquals(existente.getSenha(), captor.getValue().getSenha(),
                "sem nova senha, o hash anterior deve ser preservado");
        assertNotEquals("SenhaAntiga123", captor.getValue().getSenha(),
                "o hash preservado continua sendo um hash, nao a senha em texto puro");
    }

    @Test
    @DisplayName("A resposta da API nunca contem o campo senha")
    void respostaNaoExpoeASenha() {
        boolean temCampoSenha = false;
        for (java.lang.reflect.Field campo : EmpresaResponse.class.getDeclaredFields()) {
            if ("senha".equalsIgnoreCase(campo.getName())) {
                temCampoSenha = true;
            }
        }
        assertTrue(!temCampoSenha, "o DTO de resposta nao pode ter campo de senha");
    }
}
