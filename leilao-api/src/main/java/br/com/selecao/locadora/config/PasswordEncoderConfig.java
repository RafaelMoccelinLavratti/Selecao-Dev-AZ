package br.com.selecao.locadora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ============================================================================
 *  CONFIGURACAO: CODIFICADOR DE SENHA (BCrypt)
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
 *  POR QUE BCrypt E NAO SHA-256/MD5?
 *  1) "SALT" (serragem): o BCrypt sorteia um valor aleatorio e o mistura
 *     no hash. Duas empresas com a MESMA senha ficam com hashes DIFERENTES.
 *     Sem sal, um atacante que rouba o banco poderia casar hashes
 *     iguais e descobrir que varios usuarios compartilham a mesma senha
 *     (e testar contra outros sistemas). Com sal, isso e impossivel.
 *  2) "LENTIDAO PROPOSITAL": o BCrypt e configurado para ser caro de
 *     computar (custo 10 = ~100ms por hash). Um ataque de forca bruta
 *     testaria milhoes de senhas por segundo contra um SHA-256, mas
 *     contra o BCrypt testaria poucas por segundo. O custo sai do
 *     atacante e entra no servidor, que so faz isso uma vez por login.
 *  3) "SAL EMBUTIDO": o proprio hash carrega o sal e o custo. Por isso
 *     nao precisamos guardar o sal em outra coluna.
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
     * Define o "encarregado" de codificar e conferir senhas.
     *
     * @param passwordEncoder o bean criado aqui
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // O numero 10 e o "custo" do BCrypt: 2^10 = 1024 iteracoes.
        // 10 e o equilibrio recomendado pela propria biblioteca Spring
        // Security (seguro o suficiente e rapido o suficiente para login).
        return new BCryptPasswordEncoder(10);
    }
}
