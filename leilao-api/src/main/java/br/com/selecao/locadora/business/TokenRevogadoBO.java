package br.com.selecao.locadora.business;

import br.com.selecao.locadora.entity.TokenRevogado;
import br.com.selecao.locadora.repository.TokenRevogadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Lista de bloqueio das sessoes revogadas.
 *
 * O token de acesso e um JWT stateless: para verifica-lo, o servidor so
 * precisa da chave publica e do prazo de validade. Nao ha consulta ao
 * banco, e por isso que o token escala bem.
 *
 * O preco disso e' que o logout nao encerra a sessao de imediato: o token
 * continua valido ate expirar (15 minutos). Quem roubou o token continua
 * entrando durante esse tempo.
 *
 * Esta classe fecha a porta. O token de acesso carrega o identificador da
 * familia de sessao (claim "fam"). Quando a sessao e revogada - no
 * logout ou na deteccao de reuso do token de renovacao - a familia entra
 * nesta lista, e o filtro de JWT passa a recusar qualquer token dela.
 *
 * O custo e' uma consulta ao banco por requisicao autenticada. E o
 * preco justo: sem ela, o logout e apenas uma sugestao.
 */
@Component
public class TokenRevogadoBO {

    private static final Logger LOG = LoggerFactory.getLogger(TokenRevogadoBO.class);

    /**
     * Repositorio da lista de bloqueio.
     */
    @Autowired
    private TokenRevogadoRepository tokenRevogadoRepository;

    /**
     * Registra a revogacao de uma familia de sessao.
     *
     * Se a familia ja estiver na lista, o registro e atualizado em vez
     * de duplicado - a chave e primaria, e a mesma sessao pode ser
     * revogada mais de uma vez (logout seguido de deteccao de reuso).
     *
     * @param familia identificador da familia de sessao
     * @param usuario dono da sessao, para o log
     * @param expiraEm instante em que o registro pode ser apagado
     */
    @Transactional
    public void revogarFamilia(String familia, String usuario, LocalDateTime expiraEm) {

        if (familia == null || familia.trim().isEmpty()) {
            return;
        }

        Optional<TokenRevogado> existente = tokenRevogadoRepository.findById(familia);

        if (existente.isPresent()) {
            // Ja estava revogada. Apenas o prazo e estendido, para o
            // registro valer enquanto ainda houver token valido na
            // familia.
            TokenRevogado registro = existente.get();
            registro.setExpiraEm(expiraEm);
            tokenRevogadoRepository.save(registro);
            return;
        }

        TokenRevogado registro = new TokenRevogado();
        registro.setChave(familia);
        registro.setUsuario(usuario);
        registro.setRevogadoEm(LocalDateTime.now());
        registro.setExpiraEm(expiraEm);

        tokenRevogadoRepository.save(registro);

        LOG.info("AUTENTICACAO | SESSAO REVOGADA | familia={} usuario={} valida_ate={}",
                familia, usuario, expiraEm);
    }

    /**
     * Diz se a familia da sessao esta revogada.
     *
     * Um registro cujo prazo ja passou e' considerado valido: nenhum
     * token da familia pode valer alem dele, entao o registro nao
     * bloqueia mais nada.
     *
     * @param familia identificador da familia de sessao
     * @return true se a sessao foi revogada e o bloqueio ainda vale
     */
    @Transactional(readOnly = true)
    public boolean familiaRevogada(String familia) {

        if (familia == null || familia.trim().isEmpty()) {
            // Token sem familia: nao ha como saber se foi revogado.
            // Isso acontece com tokens emitidos antes da revogacao
            // existir, e com tokens montados a mao.
            return false;
        }

        Optional<TokenRevogado> registro = tokenRevogadoRepository.findById(familia);

        if (!registro.isPresent()) {
            return false;
        }

        return !registro.get().getExpiraEm().isBefore(LocalDateTime.now());
    }

    /**
     * Remove os registros cujo prazo ja passou.
     *
     * @return quantos registros foram removidos
     */
    @Transactional
    public int limparExpirados() {
        return tokenRevogadoRepository.removerExpirados(LocalDateTime.now());
    }
}
