package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.LoginRequest;
import br.com.selecao.locadora.dto.LoginResponse;
import br.com.selecao.locadora.entity.RefreshToken;
import br.com.selecao.locadora.exception.TokenInvalidoException;
import br.com.selecao.locadora.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Regras de negocio da autenticacao.
 *
 * O login confere a senha e emite o par de tokens. A renovacao consome o
 * token apresentado e emite um novo na mesma familia; se um token ja
 * usado voltar a ser apresentado, e sinal de copia - e a familia inteira
 * e revogada.
 *
 * Usuario inexistente e senha errada devolvem a mesma mensagem e levam o
 * mesmo tempo de resposta, para nao permitir enumeracao de usuarios.
 */
@Component
public class AutenticacaoBO {

    private static final Logger LOG = LoggerFactory.getLogger(AutenticacaoBO.class);

    /**
     * Servico que carrega os dados do usuario.
     */
    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * Codificador de senha (BCrypt ou Argon2id, conforme configurado).
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Servico de emissao e validacao dos tokens JWT.
     */
    @Autowired
    private TokenJwtBO tokenJwtBO;

    /**
     * Repositorio dos tokens de renovacao.
     */
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /**
     * Hash de uma senha que NAO EXISTE, usado para igualar o tempo de
     * resposta quando o usuario informado nao esta cadastrado.
     *
     * O valor e o hash BCrypt de uma senha aleatoria. E um segredo sem
     * importance: o objetivo nao e que ninguem o descubra, e sim que o
     * servidor gaste o mesmo tempo de CPU comparando contra ele.
     */
    private static final String HASH_SENHA_INESQUISTIVEL =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /**
     * Autentica um usuario e devolve o par de tokens.
     *
     * @param request usuario e senha
     * @param ip      origem da requisicao, registrada no token de renovacao
     * @return o par de tokens
     */
    @Transactional
    public LoginResponse autenticar(LoginRequest request, String ip) {

        UserDetails usuario = buscarUsuarioComDefesa(request.getUsuario());

        if (!passwordEncoder.matches(request.getSenha(), usuario.getPassword())) {

            LOG.warn("AUTENTICACAO | FALHA | usuario={} ip={}", request.getUsuario(), ip);
            throw new TokenInvalidoException("Credenciais invalidas");
        }

        LOG.info("AUTENTICACAO | SUCESSO | usuario={} ip={}", request.getUsuario(), ip);

        return emitirParDeTokens(usuario.getUsername(), UUID.randomUUID().toString(), ip);
    }

    /**
     * Busca o usuario, aplicando a defesa contra enumeracao.
     *
     * Se o usuario nao existir, devolve um objeto com o hash falso - de
     * modo que a comparacao de senha demore o mesmo tempo e o atacante
     * nao consiga distinguir os casos pelo tempo de resposta.
     */
    private UserDetails buscarUsuarioComDefesa(String nomeUsuario) {

        try {
            return userDetailsService.loadUserByUsername(nomeUsuario);
        } catch (UsernameNotFoundException e) {
            // Compara contra o hash falso para igualar o tempo de resposta.
            passwordEncoder.matches("senha-que-nunca-existe", HASH_SENHA_INESQUISTIVEL);
            throw new TokenInvalidoException("Credenciais invalidas");
        }
    }

    /**
     * Renova o token de acesso, rodando o token de renovacao.
     *
     * @param tokenRenovacao o token de renovacao apresentado
     * @param ip             origem da requisicao
     * @return um par novo de tokens
     */
    @Transactional
    public LoginResponse renovar(String tokenRenovacao, String ip) {

        // 1) A assinatura e verificada cryptographicamente. Um token
        //    forjado nem chega a procurar no banco.
        Claims claims = tokenJwtBO.validarTokenRenovacao(tokenRenovacao);

        String usuario = claims.getSubject();
        String hash = TokenJwtBO.calcularHash(tokenRenovacao);

        // 2) Procura o registro do token.
        RefreshToken registro = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenInvalidoException("Token de renovacao desconhecido"));

        // 3) deteccao de reuso.
        //    Um token que JA foi usado esta sendo apresentado de novo: e
        //    a assinatura de um token copiado. A resposta e revogar toda
        //    a familia.
        if (Boolean.TRUE.equals(registro.getUtilizado())) {

            int revogados = refreshTokenRepository.revogarFamilia(
                    registro.getFamilia(), LocalDateTime.now());

            LOG.warn("AUTENTICACAO | REUSO DE TOKEN DETECTADO | usuario={} ip={} "
                            + "familia={} tokens_revogados={}",
                    usuario, ip, registro.getFamilia(), revogados);

            throw new TokenInvalidoException(
                    "Token de renovacao invalido. Todos os tokens desta sessao foram revogados.");
        }

        // 4) Token ja revogado (logout anterior ou revogacao em lote).
        if (registro.getDataRevogacao() != null) {
            throw new TokenInvalidoException("Token de renovacao revogado");
        }

        // 5) Token expirado.
        if (registro.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenInvalidoException("Token de renovacao expirado");
        }

        // 6) O token antigo e consumido. A partir daqui ele nunca mais
        //    serve - se aparecer de novo, o passo 3 dispara.
        registro.setUtilizado(Boolean.TRUE);
        registro.setDataRevogacao(LocalDateTime.now());
        refreshTokenRepository.save(registro);

        // 7) O par novo nasce na MESMA familia: e a continuacao da
        //    mesma sessao, nao uma nova.
        return emitirParDeTokens(usuario, registro.getFamilia(), ip);
    }

    /**
     * Revoga a sessao associada a um token de renovacao (logout).
     *
     * @param tokenRenovacao o token apresentado
     */
    @Transactional
    public void revogar(String tokenRenovacao) {

        String hash = TokenJwtBO.calcularHash(tokenRenovacao);

        refreshTokenRepository.findByTokenHash(hash).ifPresent(registro -> {
            int revogados = refreshTokenRepository.revogarFamilia(
                    registro.getFamilia(), LocalDateTime.now());

            LOG.info("AUTENTICACAO | LOGOUT | usuario={} familia={} tokens_revogados={}",
                    registro.getUsuario(), registro.getFamilia(), revogados);
        });
    }

    /**
     * Emite um par de tokens (acesso + renovacao) e registra o token de
     * renovacao no banco.
     */
    private LoginResponse emitirParDeTokens(String usuario, String familia, String ip) {

        String tokenAcesso = tokenJwtBO.emitirTokenAcesso(usuario);
        String tokenRenovacao = tokenJwtBO.emitirTokenRenovacao(usuario);

        LocalDateTime agora = LocalDateTime.now();

        RefreshToken registro = new RefreshToken();
        registro.setTokenHash(TokenJwtBO.calcularHash(tokenRenovacao));
        registro.setUsuario(usuario);
        registro.setFamilia(familia);
        registro.setUtilizado(Boolean.FALSE);
        registro.setIpOrigem(ip);
        registro.setDataEmissao(agora);
        registro.setDataExpiracao(paraLocalDateTime(tokenJwtBO.getValidadeTokenRenovacao()));

        refreshTokenRepository.save(registro);

        return new LoginResponse(
                tokenAcesso,
                tokenRenovacao,
                "Bearer",
                tokenJwtBO.getValidadeTokenAcesso().getSeconds(),
                usuario);
    }

    /**
     * Converte uma duracao em um instante local do servidor.
     */
    private LocalDateTime paraLocalDateTime(Duration duracao) {
        return LocalDateTime.now().plus(duracao);
    }
}
