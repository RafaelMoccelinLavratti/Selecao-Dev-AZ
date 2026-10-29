package br.com.selecao.locadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ============================================================================
 *  CONFIGURACAO: CODIFICADOR DE SENHA (Argon2id com fallback para BCrypt)
 * ============================================================================
 *
 *  POR QUE ISSO EXISTE?
 *  Um banco de dados vazado e uma situacao real de seguranca da
 *  informacao (acesso indevido, backup exposto, ataque de terceiros).
 *  Se a senha estiver guardada em texto puro ("123456"), quem tiver o
 *  banco consegue logar em TODOS os sistemas que reutilizam essa senha -
 *  e sabemos que pessoas reutilizam senha. Se guardamos apenas um HASH,
 *  o vazamento nao entrega a senha.
 *
 *  POR QUE ARGON2ID E NAO SHA-256/MD5?
 *  1) "SALT" (serragem): o Argon2id sorteia um valor aleatorio e o mistura
 *     no hash. Duas empresas com a MESMA senha ficam com hashes DIFERENTES.
 *     Sem sal, um atacante que rouba o banco poderia casar hashes
 *     iguais e descobrir que varios usuarios compartilham a mesma senha
 *     (e testar contra outros sistemas). Com sal, isso e impossivel.
 *  2) "LENTIDAO PROPOSITAL": o Argon2id e configurado para ser caro de
 *     computar E de memoria. Um ataque de forca bruta testaria milhoes
 *     de senhas por segundo contra um SHA-256, mas contra o Argon2id
 *     testaria poucas por segundo. O custo sai do atacante e entra no
 *     servidor, que so faz isso uma vez por login.
 *  3) "SAL EMBUTIDO": o proprio hash carrega o sal e os parametros. Por
 *     isso nao precisamos guardar o sal em outra coluna.
 *
 *  ------------------------------------------------------
 *  A EVOLUCAO: DE BCrypt PARA ARGON2ID
 *  ------------------------------------------------------
 *  O projeto nasceu com BCrypt. Em 2023 a OWASP passou a recomendar o
 *  Argon2id como primeira opcao no "Password Storage Cheat Sheet",
 *  porque o Argon2id resiste muito melhor a ataques com hardware
 *  dedicado (GPU/ASIC): cada tentativa de ataque consome dezenas de
 *  megabytes de memoria, o que trava a capacidade de paralelizar o
 *  ataque na GPU.
 *
 *  Trocar o encoder "de um dia para o outro", sem cuidado, quebraria o
 *  acesso de TODO mundo cujas senhas ainda estivessem em BCrypt. Por
 *  isso o bean abaixo nao e um encoder simples: e o
 *  CodificadorDeSenhaDelegado, que sabe ler os dois formatos e gravar
 *  sempre no novo.
 *
 *  DETALHE IMPORTANTE: a defesa contra forca-bruta (o filtro que
 *  limita tentativas por IP) deixa de ser um extra e vira OBRIGATORIO
 *  com Argon2id - sem ele, cada tentativa de login seria uma alocacao
 *  de 64 MiB, e o proprio login viraria um vetor de esgotamento de
 *  memoria.
 *
 *  OBSERVACAO SOBRE DEPENDENCIA:
 *  Usamos APENAS o artefato "spring-security-crypto" (a parte que so
 *  codifica e compara senha), e nao o "spring-boot-starter-security"
 *  (que ativaria autenticacao em todas as rotas). Assim a API funciona
 *  exatamente como o enunciado pede - endpoints REST abertos para
 *  leitura/escrita - mas com a senha protegida. A ativacao completa de
 *  autenticacao esta documentada no README, no perfil de producao.
 * ============================================================================
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
