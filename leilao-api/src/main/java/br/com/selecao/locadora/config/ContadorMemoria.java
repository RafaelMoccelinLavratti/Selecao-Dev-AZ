package br.com.selecao.locadora.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contadores guardados na memoria do processo.
 *
 * E a implementacao padrao: nao depende de banco, nao faz I/O e e
 * rapida. Serve para desenvolvimento, para avaliacao e para qualquer
 * instalacao com uma unica replica da API.
 *
 * O limite dela e' conhecido e esta documentado: o contador vale so
 * para esta instancia. Com varias replicas atras do balanceador, cada
 * uma conta por conta, e o limite efetivo por IP multiplica pelo
 * numero de replicas. Quem precisa do limite valendo para a API
 * inteira usa o ContadorBanco.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "memoria", matchIfMissing = true)
public class ContadorMemoria implements ContadorStore {

    /**
     * Os contadores, por chave.
     *
     * ConcurrentHashMap porque a API atende varias requisicoes ao mesmo
     * tempo (o Tomcat usa um pool de threads), e dois acessos
     * simultaneos a um HashMap comum corromperiam a estrutura.
     */
    private final Map<String, Registro> contadores = new ConcurrentHashMap<>();

    @Override
    public Contagem incrementar(String chave, Duration janela) {

        Instant agora = Instant.now();
        Instant expira = agora.plus(janela);

        // compute() calcula e atualiza o contador de forma atomica. Sem
        // isso, duas threads que chegassem ao mesmo tempo poderiam
        // sobrescrever uma a outra e perder uma ocorrencia.
        Registro registro = contadores.compute(chave, (k, existente) -> {

            if (existente == null || existente.expirada(agora)) {
                // primeira ocorrencia (ou a janela anterior ja venceu)
                return new Registro(1, expira);
            }

            existente.quantidade.incrementAndGet();
            // Cada nova ocorrencia renova a janela: e assim que o
            // controle deslizante funciona.
            existente.expiraEm = expira;
            return existente;
        });

        return new Contagem(registro.quantidade.get(), registro.expiraEm);
    }

    @Override
    public Contagem consultar(String chave) {

        Instant agora = Instant.now();
        Registro registro = contadores.get(chave);

        if (registro == null || registro.expirada(agora)) {
            return Contagem.vazia(agora);
        }

        return new Contagem(registro.quantidade.get(), registro.expiraEm);
    }

    @Override
    public int limparExpirados() {

        Instant agora = Instant.now();

        int removidos = 0;

        for (Map.Entry<String, Registro> entrada : contadores.entrySet()) {
            if (entrada.getValue().expirada(agora) && contadores.remove(entrada.getKey()) != null) {
                removidos++;
            }
        }

        return removidos;
    }

    @Override
    public int removerPorPrefixo(String prefixo) {

        if (prefixo == null || prefixo.isEmpty()) {
            return 0;
        }

        int removidos = 0;

        for (Map.Entry<String, Registro> entrada : contadores.entrySet()) {
            if (entrada.getKey().startsWith(prefixo) && contadores.remove(entrada.getKey()) != null) {
                removidos++;
            }
        }

        return removidos;
    }

    @Override
    public void limpar() {
        contadores.clear();
    }

    /**
     * O contador de uma chave.
     */
    private static class Registro {

        /**
         * Quantidade de ocorrencias dentro da janela atual.
         *
         * AtomicInteger porque varias threads podem falhar ao mesmo
         * tempo no mesmo par (usuario, IP).
         */
        private final AtomicInteger quantidade;

        /**
         * Instante em que a janela expira.
         */
        private volatile Instant expiraEm;

        /**
         * @param quantidade valor inicial do contador
         * @param expiraEm   instante de expiracao da janela
         */
        Registro(int quantidade, Instant expiraEm) {
            this.quantidade = new AtomicInteger(quantidade);
            this.expiraEm = expiraEm;
        }

        /**
         * Diz se a janela desta ocorrencia ja venceu.
         *
         * @param agora instante de referencia
         * @return true se o registro pode ser descartado
         */
        boolean expirada(Instant agora) {
            return agora.isAfter(expiraEm);
        }
    }
}
