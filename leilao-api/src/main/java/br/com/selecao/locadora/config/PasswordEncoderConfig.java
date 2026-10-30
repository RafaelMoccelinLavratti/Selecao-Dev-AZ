package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuracao do codificador de senha.
 *
 * Um banco vazado e um cenario real de seguranca da informacao. Por isso a
 * senha nunca e gravada em texto puro: o banco guarda um hash, e o hash e
 * o que a API compara.
 *
 * Usa-se apenas o artefato spring-security-crypto, e nao o starter inteiro,
 * para nao ativar a cadeia de seguranca do Spring Boot sem querer.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Memoria por hash, em KB. 65.536 KB = 64 MiB.
     *
     * Este e o parametro que faz o Argon2id diferir do BCrypt: e o
     * consumo de MEMORIA, e nao apenas de CPU, que impede o ataque
     * paralelo em GPU.
     *
     * NOTA: o valor inicializado aqui e o mesmo do padrao declarado no
     * @Value. Isso permite que a classe tambem seja instanciada FORA do
     * container do Spring (como acontece nos testes unitarios, que
     * chamam "new PasswordEncoderConfig().passwordEncoder()"), sem que
     * os parametros cheguem zerados.
     */
    @Value("${app.security.password.argon2.memory:65536}")
    private int memoriaArgon2 = 65_536;

    /**
     * Numero de passadas sobre a memoria. A OWASP recomenda 2.
     */
    @Value("${app.security.password.argon2.iterations:2}")
    private int iteracoesArgon2 = 2;

    /**
     * Numero de threads por hash. 1 e o recomendado: paralelismo alto
     * nao aumenta a resistencia e multiplica o consumo de memoria.
     */
    @Value("${app.security.password.argon2.parallelism:1}")
    private int paralelismoArgon2 = 1;

    /**
     * Tamanho do hash, em bytes. 32 e o padrao.
     */
    @Value("${app.security.password.argon2.hash-length:32}")
    private int tamanhoHashArgon2 = 32;

    /**
     * Tamanho do sal, em bytes. 16 e o padrao do Argon2.
     */
    @Value("${app.security.password.argon2.salt-length:16}")
    private int comprimentoSalArgon2 = 16;

    /**
     * Define o "encarregado" de codificar e conferir senhas.
     *
     * @param passwordEncoder o bean criado aqui
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CodificadorDeSenhaDelegado(
                comprimentoSalArgon2,
                tamanhoHashArgon2,
                paralelismoArgon2,
                memoriaArgon2,
                iteracoesArgon2);
    }
}
