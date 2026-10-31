package br.com.selecao.locadora.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Verifica a configuracao de seguranca na subida da aplicacao.
 *
 * O objetivo e simples: a API nao pode subir numa configuracao que
 * pareca segura e nao seja. Cada verificacao abaixo corresponde a um
 * jeito de a API ficar exposta sem ninguem perceber.
 *
 * As configuracoes que quebram a propria inicializacao (uma variavel de
 * ambiente obrigatoria que falta) sao resolvidas pelo proprio Spring,
 * na leitura do properties - nao ha codigo para isso aqui. O que fica
 * aqui e o que o Spring nao consegue saber:
 *
 *   - a seguranca desligada (o perfil de desenvolvimento e aberto de
 *     proposito, mas quem deploya tem de saber que esta com a porta
 *     aberta);
 *   - a senha padrao do administrador, que nunca deveria chegar a
 *     producao;
 *   - o modo de autenticacao inerte, em que o fluxo de token emite
 *     tokens que nao autenticam nada.
 */
@Component
public class ValidacaoConfiguracaoSeguranca {

    private static final Logger LOG = LoggerFactory.getLogger(ValidacaoConfiguracaoSeguranca.class);

    /**
     * A senha padrao que o perfil de desenvolvimento usa. Se ela
     * chegar a ser usada, o acesso administrativo esta com uma senha
     * que qualquer um leu no repositorio.
     */
    private static final String SENHA_PADRAO = "troque-esta-senha";

    /**
     * Liga/desliga a autenticacao. Vem da variavel de ambiente
     * SECURITY_ENABLED (padrao false = desenvolvimento aberto).
     */
    @Value("${app.security.enabled:false}")
    private boolean autenticacaoAtiva;

    /**
     * Modo de autenticacao quando a seguranca esta ligada.
     */
    @Value("${app.security.mode:basic}")
    private String modoSeguranca;

    /**
     * Senha da autenticacao HTTP Basic.
     */
    @Value("${app.security.password:troque-esta-senha}")
    private String senhaPadrao;

    /**
     * Pasta com as chaves RSA de assinatura do JWT.
     *
     * Vazio significa que o ChaveJwtProvider vai gerar um par efemero
     * na memoria - o que so funciona numa instancia so.
     */
    @Value("${app.security.jwt.keys-dir:}")
    private String pastaChaves;

    /**
     * Faz as verificacoes assim que a aplicacao sobe.
     *
     * @throws IllegalStateException se alguma configuracao perigosa for
     *                               detectada em um perfil que exige
     *                               protecao
     */
    @PostConstruct
    public void verificar() {

        if (!autenticacaoAtiva) {

            // Nao e erro: o perfil de desenvolvimento e aberto de
            // proposito, para o avaliador testar as rotas sem login.
            // Mas precisa gritar, porque e exatamente assim que uma
            // aplicacao esquecida no ar passa a ser um problema.
            LOG.warn("=".repeat(72));
            LOG.warn("SEGURANCA | ATENCAO: A AUTENTICACAO ESTA DESLIGADA.");
            LOG.warn("SEGURANCA | Todas as rotas estao abertas: qualquer um pode ler,");
            LOG.warn("SEGURANCA | alterar e excluir os dados da API. Isso serve para");
            LOG.warn("SEGURANCA | desenvolvimento e avaliacao, e nao para producao.");
            LOG.warn("=".repeat(72));

            return;
        }

        if (SENHA_PADRAO.equals(senhaPadrao)) {
            throw new IllegalStateException(
                    "A senha do administrador e a senha padrao do projeto. "
                            + "Defina SECURITY_PASSWORD antes de subir a aplicacao.");
        }

        if ("basic".equalsIgnoreCase(modoSeguranca) && pastaChaves != null && !pastaChaves.trim().isEmpty()) {
            LOG.warn("SEGURANCA | O modo de autenticacao e 'basic', mas existe uma pasta de "
                    + "chaves JWT configurada. O fluxo de token esta desligado: o "
                    + "POST /auth/login emite um par de tokens que nao autentica nada, "
                    + "porque o filtro de JWT nao entra na cadeia de seguranca. "
                    + "Se a intencao era usar token, defina SECURITY_MODE=jwt.");
        }
    }
}
