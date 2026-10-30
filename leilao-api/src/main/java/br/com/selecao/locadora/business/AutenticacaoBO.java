package br.com.selecao.locadora.business;

import br.com.selecao.locadora.dto.LoginRequest;
import br.com.selecao.locadora.dto.LoginResponse;
import br.com.selecao.locadora.config.LoginAttemptService;
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

import javax.annotation.PostConstruct;
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
 * mesmo tempo de resposta, pra nao dar pra adivinhar quais usuarios
 * existem.
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
     * Codificador de senha (Argon2id, ou BCrypt nos hashes antigos).
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
     * Controle de tentativas de login.
     *
     * O login por JSON (este metodo) e o login por HTTP Basic sao dois
     * caminhos para a mesma porta. Se so um dos dois registrasse a falha
     * no contador, o outro ficaria sem protecao: o atacante escolheria
     * o caminho sem controle e o bloqueio por IP nao valeria. Por isso os
     * dois usam este mesmo servico.
     */
    @Autowired
    private LoginAttemptService loginAttemptService;

    /**
     * Hash de uma senha que nao existe, usado pra igualar o tempo de
     * resposta quando o usuario informado nao esta cadastrado.
     *
     * Gerado aqui, na subida da aplicacao, e nao fixo no codigo: assim ele
     * sai com o mesmo algoritmo e o mesmo custo do encoder configurado. Um
     * hash BCrypt fixo gastaria bem menos CPU que um Argon2id, e a
     * diferenca de tempo entregaria exatamente o que a defesa esconde.
     *
     * O segredo em si nao importa. Ninguem precisa descobrir.
     */
    private String hashSenhaInexistente;

    /**
     * Prepara o hash de referencia logo na subida.
     */
    @PostConstruct
    public void prepararHashInexistente() {
        this.hashSenhaInexistente = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Autentica um usuario e devolve o par de tokens.
     *
     * @param request usuario e senha
     * @param ip      origem da requisicao, registrada no token de renovacao
     * @return o par de tokens
     */
    @Transactional
    public LoginResponse autenticar(LoginRequest request, String ip) {

        UserDetails usuario = buscarUsuarioComDefesa(request.getUsuario(), ip);

        if (!passwordEncoder.matches(request.getSenha(), usuario.getPassword())) {

            LOG.warn("AUTENTICACAO | FALHA | usuario={} ip={}", request.getUsuario(), ip);

            // Registra a falha no controle de tentativas. Sem esta linha,
            // o login por JSON nao contaria para o bloqueio por IP nem por
            // usuario: o contador so cresceria no caminho do HTTP Basic.
            loginAttemptService.registrarFalha(ip, request.getUsuario());

            throw new TokenInvalidoException("Credenciais invalidas");
        }

        // Login valido: zera o contador do usuario.
        loginAttemptService.registrarSucesso(usuario.getUsername());

        LOG.info("AUTENTICACAO | SUCESSO | usuario={} ip={}", request.getUsuario(), ip);

        return emitirParDeTokens(usuario.getUsername(), UUID.randomUUID().toString(), ip);
    }

    /**
     * Busca o usuario, aplicando a defesa contra enumeracao.
     *
     * Se o usuario nao existir, devolve um objeto com o hash falso - de
     * modo que a comparacao de senha demore o mesmo tempo e o atacante
     * nao consiga distinguir os casos pelo tempo de resposta.
     *
     * @param nomeUsuario o nome informado na tentativa de login
     * @param ip          origem da requisicao, usada no controle de tentativas
     * @return os dados do usuario
     */
    private UserDetails buscarUsuarioComDefesa(String nomeUsuario, String ip) {

        try {
            return userDetailsService.loadUserByUsername(nomeUsuario);
        } catch (UsernameNotFoundException e) {
            // Compara contra o hash falso pra igualar o tempo de resposta.
            passwordEncoder.matches("senha-que-nunca-existe", hashSenhaInexistente);

            // Tambem conta como falha. O usuario pode nao existir, mas o
            // atacante nao pode saber disso: a resposta e a mesma nos dois
            // casos, entao ele nao consegue distinguir. Do ponto de vista
            // do controle de tentativas, e uma tentativa a mais.
            loginAttemptService.registrarFalha(ip, nomeUsuario);

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

        // A assinatura e verificada cryptographicamente. Um token forjado
        // nem chega a procurar no banco.
        Claims claims = tokenJwtBO.validarTokenRenovacao(tokenRenovacao);

        String usuario = claims.getSubject();
        String hash = TokenJwtBO.calcularHash(tokenRenovacao);

        // Procura o registro do token.
        RefreshToken registro = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenInvalidoException("Token de renovacao desconhecido"));

        // Deteccao de reuso.
        // Um token que JA foi usado esta sendo apresentado de novo: e a
        // assinatura de um token copiado. A resposta e revogar toda a
        // familia.
        if (Boolean.TRUE.equals(registro.getUtilizado())) {

            int revogados = refreshTokenRepository.revogarFamilia(
                    registro.getFamilia(), LocalDateTime.now());

            LOG.warn("AUTENTICACAO | REUSO DE TOKEN DETECTADO | usuario={} ip={} "
                            + "familia={} tokens_revogados={}",
                    usuario, ip, registro.getFamilia(), revogados);

            throw new TokenInvalidoException(
                    "Token de renovacao invalido. Todos os tokens desta sessao foram revogados.");
        }

        // Token ja revogado (logout anterior ou revogacao em lote).
        if (registro.getDataRevogacao() != null) {
            throw new TokenInvalidoException("Token de renovacao revogado");
        }

        // Token expirado.
        if (registro.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenInvalidoException("Token de renovacao expirado");
        }

        // O token antigo e consumido. A partir daqui ele nunca mais serve -
        // se aparecer de novo, o passo de reuso acima dispara.
        registro.setUtilizado(Boolean.TRUE);
        registro.setDataRevogacao(LocalDateTime.now());
        refreshTokenRepository.save(registro);

        // O par novo nasce na mesma familia: e a continuacao da mesma
        // sessao, nao uma nova.
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
