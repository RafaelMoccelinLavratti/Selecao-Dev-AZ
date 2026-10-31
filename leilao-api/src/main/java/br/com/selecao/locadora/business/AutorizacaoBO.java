package br.com.selecao.locadora.business;

import br.com.selecao.locadora.config.UsuarioAtual;
import br.com.selecao.locadora.entity.Comprador;
import br.com.selecao.locadora.entity.Leilao;
import br.com.selecao.locadora.entity.Lote;
import br.com.selecao.locadora.exception.AcessoNegadoException;
import br.com.selecao.locadora.repository.CompradorRepository;
import br.com.selecao.locadora.repository.LeilaoRepository;
import br.com.selecao.locadora.repository.LoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Regras de acesso: quem pode ver e mexer em cada recurso.
 *
 * O modelo de dados do projeto e multi-empresa: uma empresa vende
 * leiloes, outra compra, e as duas nao deveriam enxergar os dados uma
 * da outra. O banco ja guardava essa informacao (a coluna "vendedor" do
 * leilao, a tabela de vinculo de comprador, e o proprio login da
 * empresa). O que faltava era o controle de acesso que usa essa
 * informacao.
 *
 * As regras:
 *
 *   ADMIN    ve e mexe em tudo.
 *
 *   EMPRESA  ve os proprios dados, os leiloes que vende e os leiloes
 *            em que esta inscrita como compradora. Pode alterar apenas
 *            os leiloes que vende. Unidades (os locais dos leiloes) sao
 *            dado de referencia: todo mundo ve, so o admin escreve.
 *
 * A diferenca entre "nao pode ver" e "nao existe" e intencional: as
 * mensagens de negacao nao dizem se o recurso existe. Entregar essa
 * informacao permitiria ao atacante descobrir quais ids sao validos.
 *
 * Quando a autenticacao esta desligada (o perfil de desenvolvimento),
 * nenhum controle e aplicado: o objetivo ali e poder navegar em tudo
 * sem login.
 */
@Component
public class AutorizacaoBO {

    /**
     * Mensagem de negacao.
     *
     * A mesma para qualquer recurso negado. Ela diz que o acesso foi
     * recusado, sem dizer se o recurso existe.
     */
    private static final String MENSAGEM_NEGADA =
            "Voce nao tem permissao para acessar este recurso.";

    /**
     * Repositorio dos leiloes.
     */
    @Autowired
    private LeilaoRepository leilaoRepository;

    /**
     * Repositorio dos lotes.
     */
    @Autowired
    private LoteRepository loteRepository;

    /**
     * Repositorio dos vinculos de comprador.
     */
    @Autowired
    private CompradorRepository compradorRepository;

    /**
     * Exige o papel de administrador.
     *
     * Usado nas operacoes que sao da administracao da API: cadastrar
     * empresa, escrever unidades, ler a trilha de auditoria.
     *
     * @param operacao nome da operacao, para a mensagem
     */
    public void exigirAdministrador(String operacao) {

        // Sem autenticacao (perfil de desenvolvimento): nao ha controle.
        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (!UsuarioAtual.ehAdministrador()) {
            throw new AcessoNegadoException(
                    "Somente o administrador pode " + operacao + ".");
        }
    }

    /**
     * Exige que o usuario seja o administrador ou o proprio dono do
     * recurso.
     *
     * @param empresaIdDono id da empresa dona do recurso
     */
    public void exigirProprioOuAdministrador(Integer empresaIdDono) {

        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (UsuarioAtual.ehAdministrador()) {
            return;
        }

        Integer empresaDoUsuario = UsuarioAtual.empresaId().orElse(null);

        if (empresaDoUsuario == null || !empresaDoUsuario.equals(empresaIdDono)) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }
    }

    /**
     * Exige que o usuario possa ver o leilao informado.
     *
     * Pode ver quando e' o vendedor ou quando esta inscrita como
     * compradora.
     *
     * @param leilaoId id do leilao
     */
    public void exigirLeituraLeilao(Integer leilaoId) {

        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (UsuarioAtual.ehAdministrador()) {
            return;
        }

        Integer empresaId = UsuarioAtual.empresaId().orElse(null);

        if (empresaId == null || !podeVerLeilao(empresaId, leilaoId)) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }
    }

    /**
     * Exige que o usuario possa alterar o leilao informado.
     *
     * So o vendedor altera o proprio leilao. O administrador altera
     * qualquer um.
     *
     * @param leilaoId id do leilao
     */
    public void exigirEscritaLeilao(Integer leilaoId) {

        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (UsuarioAtual.ehAdministrador()) {
            return;
        }

        Integer empresaId = UsuarioAtual.empresaId().orElse(null);

        if (empresaId == null || !ehVendedora(empresaId, leilaoId)) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }
    }

    /**
     * Exige que o usuario possa alterar o leilao informado sem passar
     * o leilao de maos.
     *
     * Sao duas verificacoes, e as duas importam:
     *
     *   - o leilao precisa ser alteravel por quem pede: o vendedor, ou
     *     o administrador;
     *
     *   - o novo vendedor precisa ser o proprio usuario (ou o
     *     administrador).
     *
     * Por que a segunda verificacao existe?
     *
     * A primeira, sozinha, nao fecha a porta. Ela responde "esta pessoa
     * pode mexer neste leilao agora?". Mas a alteracao tambem pode
     * trocar QUEM e o vendedor - e essa troca nao passa pela primeira
     * verificacao, porque ela olha o leilao como ele esta, e nao como
     * ele vai ficar.
     *
     * Sem a segunda, uma empresa que vende um leilao poderia
     * transferi-lo para outra empresa. Perderia o controle sobre o
     * proprio leilao, mas o mais grave e o caminho inverso: a empresa
     * que passa a ser a vendedora ganha o mesmo poder de escrita, e
     * pode repetir a operacao com o leilao de outra empresa. O
     * controle de acesso viraria uma roleta: quem gira mais vezes
     * leva.
     *
     * @param leilaoId       id do leilao a alterar
     * @param novoVendedorId id da empresa que vai ficar como vendedora
     */
    public void exigirEscritaLeilaoComVendedor(Integer leilaoId, Integer novoVendedorId) {

        exigirEscritaLeilao(leilaoId);
        exigirProprioOuAdministrador(novoVendedorId);
    }

    /**
     * Exige que o usuario possa ver o lote informado.
     *
     * O lote herda o acesso do leilao a que pertence.
     *
     * @param loteId id do lote
     */
    public void exigirLeituraLote(Integer loteId) {

        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (UsuarioAtual.ehAdministrador()) {
            return;
        }

        Integer empresaId = UsuarioAtual.empresaId().orElse(null);

        if (empresaId == null) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }

        Optional<Lote> lote = loteRepository.findById(loteId);

        if (!lote.isPresent()) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }

        Integer leilaoId = lote.get().getLeilao() == null ? null : lote.get().getLeilao().getId();

        if (leilaoId == null || !podeVerLeilao(empresaId, leilaoId)) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }
    }

    /**
     * Exige que o usuario possa alterar o lote informado.
     *
     * O lote herda a regra de escrita do leilao: so o vendedor do
     * leilao acrescenta, altera ou exclui os lotes.
     *
     * @param loteId id do lote
     */
    public void exigirEscritaLote(Integer loteId) {

        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (UsuarioAtual.ehAdministrador()) {
            return;
        }

        Integer empresaId = UsuarioAtual.empresaId().orElse(null);

        if (empresaId == null) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }

        Optional<Lote> lote = loteRepository.findById(loteId);

        if (!lote.isPresent()) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }

        Integer leilaoId = lote.get().getLeilao() == null ? null : lote.get().getLeilao().getId();

        if (leilaoId == null || !ehVendedora(empresaId, leilaoId)) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }
    }

    /**
     * Exige que o usuario possa alterar o lote informado sem move-lo
     * para o leilao de outra empresa.
     *
     * Sao duas verificacoes, e as duas importam:
     *
     *   - o lote precisa ser alteravel por quem pede: o vendedor do
     *     leilao em que o lote esta, ou o administrador;
     *
     *   - o novo leilao do lote precisa ser um leilao que o usuario
     *     vende (ou o administrador).
     *
     * A segunda existe pelo mesmo motivo da troca de vendedor no
     * leilao: a verificacao do dono atual responde "esta pessoa pode
     * mexer neste lote agora?", mas nao responde "em que leilao este
     * lote vai parar?". Sem ela, uma empresa poderia mover o lote para
     * o leilao de outra empresa - ou trazê-lo para o proprio leilao e
     * ficar com o controle do valor inicial de um item que pertence a
     * outra.
     *
     * @param loteId        id do lote a alterar
     * @param novoLeilaoId id do leilao em que o lote vai ficar
     */
    public void exigirEscritaLoteComLeilao(Integer loteId, Integer novoLeilaoId) {

        exigirEscritaLote(loteId);
        exigirEscritaLeilao(novoLeilaoId);
    }

    /**
     * Exige que o usuario possa ver o vinculo de comprador informado.
     *
     * @param empresaIdVinculo id da empresa do vinculo
     */
    public void exigirLeituraComprador(Integer empresaIdVinculo) {

        if (!UsuarioAtual.usuario().isPresent()) {
            return;
        }

        if (UsuarioAtual.ehAdministrador()) {
            return;
        }

        Integer empresaId = UsuarioAtual.empresaId().orElse(null);

        if (empresaId == null || !empresaId.equals(empresaIdVinculo)) {
            throw new AcessoNegadoException(MENSAGEM_NEGADA);
        }
    }

    /**
     * Diz se a empresa pode ver o leilao.
     *
     * @param empresaId id da empresa
     * @param leilaoId  id do leilao
     * @return true se pode ver
     */
    public boolean podeVerLeilao(Integer empresaId, Integer leilaoId) {

        if (empresaId == null || leilaoId == null) {
            return false;
        }

        return ehVendedora(empresaId, leilaoId) || ehCompradora(empresaId, leilaoId);
    }

    /**
     * A empresa e' a vendedora do leilao?
     */
    private boolean ehVendedora(Integer empresaId, Integer leilaoId) {

        Optional<Leilao> leilao = leilaoRepository.findById(leilaoId);

        if (!leilao.isPresent() || leilao.get().getVendedor() == null) {
            return false;
        }

        return empresaId.equals(leilao.get().getVendedor().getId());
    }

    /**
     * A empresa esta inscrita como compradora do leilao?
     */
    private boolean ehCompradora(Integer empresaId, Integer leilaoId) {

        List<Comprador> vinculos = compradorRepository.findByIdLeilao(leilaoId);

        return vinculos.stream()
                .anyMatch(vinculo -> vinculo.getId() != null
                        && empresaId.equals(vinculo.getId().getEmpresa()));
    }
}
