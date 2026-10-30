package br.com.selecao.locadora.config;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codificador de senha que aceita Argon2id e BCrypt.
 *
 * A base ja tem senhas em BCrypt, e a OWASP passou a recomendar Argon2id
 * como primeira opcao. Trocar o codificador direto deixaria ninguem
 * conseguir entrar, entao esta classe identifica o algoritmo pelo formato
 * do hash gravado e grava sempre em Argon2id.
 *
 * O hash antigo e reescrito sozinho no primeiro login: a base migra no
 * ritmo do uso, sem script e sem janela de manutencao.
 */
public class CodificadorDeSenhaDelegado implements PasswordEncoder {

    /**
     * Prefixo que identifica um hash em Argon2id.
     */
    private static final String PREFIXO_ARGON2 = "{argon2}";

    /**
     * Prefixo que identifica um hash em BCrypt.
     */
    private static final String PREFIXO_BCRYPT = "{bcrypt}";

    /**
     * Identificador do algoritmo padrao (o usado pra gravar senhas novas).
     */
    private static final String ID_PADRAO = "argon2";

    /**
     * Motor de delegacao do proprio Spring Security: resolve o encoder
     * correto a partir do prefixo "{id}" gravado no hash.
     */
    private final DelegatingPasswordEncoder delegador;

    /**
     * Codificador de BCrypt, mantido pra ler os hashes ja gravados no
     * formato antigo (sem prefixo).
     */
    private final BCryptPasswordEncoder bcryptLegado;

    /**
     * Construtor com os parametros de Argon2id explicitos.
     *
     * Um detalhe sobre a assinatura: no Spring Security 5.7 a ordem e
     * (saltLength, hashLength, parallelism, memory, iterations) - a
     * memoria e o penultimo parametro, e o valor e em KB.
     *
     * @param comprimentoSal    tamanho do sal, em bytes (16 e o padrao)
     * @param tamanhoHash       tamanho do hash, em bytes (32 e o padrao)
     * @param paralelismo       numero de threads por hash
     * @param memoria           memoria por hash, em KB (65.536 = 64 MiB)
     * @param iteracoes         numero de passadas sobre a memoria
     */
    public CodificadorDeSenhaDelegado(int comprimentoSal,
                                       int tamanhoHash,
                                       int paralelismo,
                                       int memoria,
                                       int iteracoes) {

        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                comprimentoSal, tamanhoHash, paralelismo, memoria, iteracoes);

        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("argon2", argon2);
        encoders.put("bcrypt", new BCryptPasswordEncoder(10));

        this.delegador = new DelegatingPasswordEncoder(ID_PADRAO, encoders);
        this.bcryptLegado = new BCryptPasswordEncoder(10);
    }

    /**
     * Codifica a senha, sempre usando Argon2id.
     *
     * O prefixo "{argon2}" e gravado junto com o hash. E ele que permite
     * trocar o algoritmo de novo no futuro sem perder as senhas - e o que
     * permite ler hoje os hashes em BCrypt sem prefixo.
     *
     * @param senhaBruta a senha em texto puro
     * @return o hash, com prefixo de algoritmo
     */
    @Override
    public String encode(CharSequence senhaBruta) {
        return delegador.encode(senhaBruta);
    }

    /**
     * Confere a senha contra o hash gravado, escolhendo o algoritmo certo
     * pelo formato do hash.
     *
     * @param senhaBruta  a senha em texto puro
     * @param hashGravado o hash como esta no banco
     * @return true se a senha confere
     */
    @Override
    public boolean matches(CharSequence senhaBruta, String hashGravado) {

        if (senhaBruta == null || hashGravado == null) {
            return false;
        }

        // Formato com prefixo: o motor do Spring resolve sozinho.
        if (hashGravado.startsWith(PREFIXO_ARGON2) || hashGravado.startsWith(PREFIXO_BCRYPT)) {
            return delegador.matches(senhaBruta, hashGravado);
        }

        // Formatos antigos, sem prefixo. Ainda existem no banco deste
        // projeto (gravados antes da migracao pra Argon2id) e precisam
        // continuar funcionando.
        if (hashGravado.startsWith("$argon2")) {
            return delegador.matches(senhaBruta, PREFIXO_ARGON2 + hashGravado);
        }

        if (hashGravado.startsWith("$2")) {
            return bcryptLegado.matches(senhaBruta, hashGravado);
        }

        // Formato desconhecido: a senha nao pode ser conferida, e recusar
        // e mais seguro do que aceitar.
        throw new IllegalArgumentException("Formato de hash de senha nao reconhecido.");
    }

    /**
     * Indica se o hash precisa ser refeito com o algoritmo atual.
     *
     * O Spring Security chama este metodo depois de um login bem-sucedido:
     * se a resposta for "true", o hash e regravado no formato novo. E o que
     * faz a migracao do BCrypt pra Argon2id acontecer sozinha, sem script
     * e sem janela de manutencao.
     *
     * @param hashGravado o hash como esta no banco
     * @return true se o hash deve ser regravado
     */
    @Override
    public boolean upgradeEncoding(String hashGravado) {
        return hashGravado == null || !hashGravado.startsWith(PREFIXO_ARGON2);
    }
}
