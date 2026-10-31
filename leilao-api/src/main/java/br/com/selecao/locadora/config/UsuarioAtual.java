package br.com.selecao.locadora.config;

import br.com.selecao.locadora.business.UsuarioAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Le o usuario autenticado do contexto de seguranca.
 *
 * A pergunta "quem esta chamando?" e' feita em varios lugares: no
 * controller, para checar o acesso; na trilha de auditoria, para gravar
 * quem fez a operacao. Este helper e' a unica forma de fazer essa
 * pergunta, para que a resposta seja sempre a mesma.
 *
 * Quando nao ha ninguem autenticado - o perfil de desenvolvimento, com a
 * seguranca desligada - os metodos devolvem vazio, e quem chama decide
 * o que fazer (em geral, permitir, porque o controle de acesso esta
 * desligado de proposito).
 */
public final class UsuarioAtual {

    /**
     * Classe utilitária: nao pode ser instanciada.
     */
    private UsuarioAtual() {
        throw new IllegalStateException("Classe utilitária: não deve ser instanciada.");
    }

    /**
     * O usuario autenticado, se houver.
     *
     * @return os dados do usuario, ou vazio
     */
    public static Optional<UsuarioAutenticado> usuario() {

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = autenticacao.getPrincipal();

        if (principal instanceof UsuarioAutenticado) {
            return Optional.of((UsuarioAutenticado) principal);
        }

        // O Spring cria um "anonymousUser" quando nao ha login. Ele nao
        // e' um UsuarioAutenticado, e nao tem empresa nenhuma.
        return Optional.empty();
    }

    /**
     * O nome do usuario autenticado.
     *
     * @return o nome, ou "anonimo" se nao houver ninguem autenticado
     */
    public static String nome() {
        return usuario().map(UsuarioAutenticado::getUsername).orElse("anonimo");
    }

    /**
     * O id da empresa do usuario autenticado.
     *
     * @return o id da empresa, ou vazio se for o administrador (ou se
     *         nao houver ninguem autenticado)
     */
    public static Optional<Integer> empresaId() {
        return usuario().map(UsuarioAutenticado::getEmpresaId);
    }

    /**
     * Diz se o usuario autenticado e' o administrador da API.
     *
     * @return true se for o administrador
     */
    public static boolean ehAdministrador() {
        return usuario().map(UsuarioAutenticado::ehAdministrador).orElse(false);
    }

    /**
     * O escopo de leitura: de qual empresa os dados podem ser vistos.
     *
     *   - null  = sem restricao. E o caso do administrador (que ve
     *             tudo) e do perfil de desenvolvimento (que tem a
     *             autenticacao desligada de proposito).
     *   - um id = somente os dados daquela empresa.
     *
     * O metodo existe para que a distincao fique explicita nas telas.
     * A alternativa - "sem id de empresa, entao nao filtra" - parece
     * equivalente, mas e' um beco sem saida: um token de empresa sem o
     * vinculo cairia no caminho sem filtro e veria tudo. Aqui o token
     * assim ja e' recusado na autenticacao.
     *
     * @return o id da empresa, ou null se nao ha restricao
     */
    public static Integer escopoEmpresa() {

        Optional<UsuarioAutenticado> usuario = usuario();

        if (!usuario.isPresent()) {
            // Perfil de desenvolvimento: sem autenticacao, sem restricao.
            return null;
        }

        if (usuario.get().ehAdministrador()) {
            return null;
        }

        return usuario.get().getEmpresaId();
    }
}
