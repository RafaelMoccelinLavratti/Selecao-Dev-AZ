package br.com.selecao.locadora.config;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 *  CODIFICADOR DE SENHA DELEGADO (Argon2id com fallback para BCrypt)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O PROBLEMA QUE ESTA CLASSE RESOLVE
 *  ------------------------------------------------------
 *  A empresa usa BCrypt desde o inicio do projeto. A OWASP, no seu
 *  "Password Storage Cheat Sheet" de 2023, passou a recomendar ARGON2ID
 *  como primeira opcao - porque ele resiste a ataques com hardware
 *  dedicado (GPU, ASIC) MUITO melhor que o BCrypt.
 *
 *  Mas nao se pode simplesmente trocar o encoder: as senhas ja gravadas
 *  no banco estao em BCrypt, e se o sistema passar a so aceitar Argon2,
 *  NINGUEM CONSEGUIRA MAIS ENTRAR.
 *
 *  ------------------------------------------------------
 *  A SOLUCAO: DELEGACAO
 *  ------------------------------------------------------
 *  Esta classe sabe IDENTIFICAR o algoritmo do hash que ja esta gravado e
 *  verifica a senha com o algoritmo correspondente:
 *
 *      hash no banco            ->  como a senha e conferida
 *      -------------------------------------------------------
 *      {argon2}$argon2id$...     ->  Argon2id
 *      {bcrypt}$2a$10$...        ->  BCrypt
 *      $2a$10$... (sem prefixo)  ->  BCrypt  (formato antigo, do projeto
 *                                   original - continua funcionando)
 *      $argon2id$... (sem pref.) ->  Argon2id
 *
 *  E ao GRAVAR, usa sempre o melhor algoritmo disponivel (Argon2id).
 *  E ai entra o segundo recurso, que e o mais elegante:
 *
 *  ------------------------------------------------------
 *  A REVALIDACAO AUTOMATICA ("upgrade encoding")
 *  ------------------------------------------------------
 *  Quando um usuario entra com sucesso e sua senha ainda esta em BCrypt,
 *  o sistema reescreve o hash no formato novo, sem que o usuario perceba
 *  nada.
 *
 *  Isso significa que a base migra sozinha, no ritmo do uso. Nao existe
 *  "janela de migracao", nem script de conversao em lote, nem risco de
 *  deixar um usuario sem acesso. Quem loga hoje ja fica em Argon2id.
 *
 *  ------------------------------------------------------
 *  POR QUE NAO USAR SO O "DelegatingPasswordEncoder" DO SPRING?
 *  ------------------------------------------------------
 *  Porque o DelegatingPasswordEncoder oficial exige que TODOS os hashes
 *  gravados tenham o prefixo "{id}". Se um hash antigo nao tem prefixo,
 *  ele lanca IllegalArgumentException ("There is no PasswordEncoder mapped
 *  for the id 'null'") - e o login de quem ja tem senha no banco quebra.
 *
 *  Aqui o DelegatingPasswordEncoder do Spring e usado como MOTOR (ele
 *  sabe escolher o encoder certo a partir do prefixo), e esta classe
 *  adiciona por cima a unica regra que falta: reconhecer o hash legado
 *  sem prefixo e ainda assim reescreve-lo no formato novo.
 *
 *  ------------------------------------------------------
 *  POR QUE ARGON2ID E MELHOR QUE BCrypt
 *  ------------------------------------------------------
 *      - Argon2id usa MEMORIA como recurso principal. Um ataque com GPU
 *        (que tem miles de nucleos e muita memoria) tem dificuldade
 *        grande de paralelizar ataques ao Argon2, porque cada tentativa
 *        consome dezenas de megabytes de memoria.
 *      - O BCrypt tem custo computacional, mas usa pouca memoria - e
 *        GPUs sao exceptionally boas em calcular em massa.
 *      - Argon2id e a variante HIBRIDA: tem a resistencia do Argon2i
 *        contra ataque de tempo e a do Argon2d contra ataque de GPU.
 *
 *  PARAMETROS USADOS (recomendacao da OWASP, "m=19MiB, t=2, p=1" ou mais):
 *      - memoria:      65.536 KB (64 MiB) por hash;
 *      - iteracoes:    2;
 *      - paralelismo:  1;
 *      - tamanho do hash: 32 bytes;
 *      - sal:          16 bytes, sorteado a cada codificacao.
 *
 *  ------------------------------------------------------
 *  O CUSTO PARA O SERVIDOR
 *  ------------------------------------------------------
 *  Argon2id com 64 MiB e MUITO mais caro que o BCrypt: da ordem de
 *  100 ms por verificacao, consumindo memoria. Em um login por segundo,
 *  isso sao 64 MB/s de memoria - o que exige dimensionar o pool de
 *  conexoes e o numero de replicas do servidor. E o motivo pelo qual
 *  o controle anti-forca-bruta (que limita as tentativas por IP) e
 *  obrigatorio quando se usa Argon2id: sem ele, cada tentativa de login
 *  seria uma operacao de 64 MiB, e o proprio login viraria um vetor de
 *  esgotamento de memoria.
 * ============================================================================
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
     * Identificador do algoritmo PADRAO (o usado para gravar novas senhas).
     */
    private static final String ID_PADRAO = "argon2";

    /**
     * Motor de delegacao do proprio Spring Security: resolve o encoder
     * correto a partir do prefixo "{id}" gravado no hash.
     */
    private final DelegatingPasswordEncoder delegador;

    /**
     * Codificador de BCrypt, mantido para ler os hashes ja gravados
     * no formato antigo (sem prefixo).
     */
    private final BCryptPasswordEncoder bcryptLegado;

    /**
     * Construtor com os parametros de Argon2id explicitos.
     *
     * NOTA SOBRE A ASSINATURA: no Spring Security 5.7 a ordem e
     * (saltLength, hashLength, parallelism, memory, iterations) - a
     * memoria e o PENULTIMO parametro, e o valor e em KB.
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
     * O prefixo "{argon2}" e gravado junto com o hash. E ele que permite,
     * no futuro, trocar o algoritmo de novo sem perder as senhas - e o
     * que permite ler hoje os hashes em BCrypt sem prefixo.
     *
     * @param senhaBruta a senha em texto puro
     * @return o hash, com prefixo de algoritmo
     */
    @Override
    public String encode(CharSequence senhaBruta) {
        return delegador.encode(senhaBruta);
    }

    /**
     * Confere a senha contra o hash gravado, escolhendo o algoritmo
     * certo pelo formato do hash.
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

        // Formatos ANTIGOS, sem prefixo. Ainda existem no banco deste
        // projeto (gravados antes da migracao para Argon2id) e precisam
        // continuar funcionando.
        if (hashGravado.startsWith("$argon2")) {
            return delegador.matches(senhaBruta, PREFIXO_ARGON2 + hashGravado);
        }

        if (hashGravado.startsWith("$2")) {
            return bcryptLegado.matches(senhaBruta, hashGravado);
        }

        // Formato desconhecido: a senha nao pode ser conferida, e
        // recusar e mais seguro do que aceitar.
        throw new IllegalArgumentException("Formato de hash de senha nao reconhecido.");
    }

    /**
     * Indica se o hash precisa ser refeito com o algoritmo atual.
     *
     * O Spring Security chama este metodo depois de um login bem
     *-sucedido: se a resposta for "true", o hash e regravado no formato
     * novo. E o que faz a migracao do BCrypt para o Argon2id acontecer
     * sozinha, sem script e sem janela de manutencao.
     *
     * @param hashGravado o hash como esta no banco
     * @return true se o hash deve ser regravado
     */
    @Override
    public boolean upgradeEncoding(String hashGravado) {
        return hashGravado == null || !hashGravado.startsWith(PREFIXO_ARGON2);
    }
}
