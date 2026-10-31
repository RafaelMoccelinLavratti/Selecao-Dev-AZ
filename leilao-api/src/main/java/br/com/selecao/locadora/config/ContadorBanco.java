package br.com.selecao.locadora.config;

import br.com.selecao.locadora.entity.ContadorSeguranca;
import br.com.selecao.locadora.repository.ContadorSegurancaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Contadores guardados no banco.
 *
 * E a implementacao para quando a API roda em mais de uma replica.
 *
 * O problema que ela resolve: com o contador na memoria, cada replica
 * conta por conta. O atacante distribui as tentativas entre as replicas
 * (ou escolhe a replica pelo cabecalho X-Forwarded-For) e o limite
 * efetivo por IP passa a ser o limite configurado vezes o numero de
 * replicas. O limite por usuario, que depende do IP, some de vez.
 *
 * Com o contador no banco, o limite vale para a API inteira.
 *
 * O preco e' uma ida ao banco a cada requisicao. Em troca, o limite
 * funciona. E' a escolha certa quando o limite e' o que segura o
 * ataque; e' desperdicio quando a API tem uma replica so - por isso o
 * ContadorMemoria e' o padrao.
 *
 * Para usar: app.security.rate-limit.store=banco
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "banco")
public class ContadorBanco implements ContadorStore {

    /**
     * Repositorio dos contadores.
     */
    @Autowired
    private ContadorSegurancaRepository contadorSegurancaRepository;

    @Override
    @Transactional
    public Contagem incrementar(String chave, Duration janela) {

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime expira = agora.plus(janela);

        // O incremento e' atomico no banco (ver o repositorio). Depois
        // dele, a leitura devolve o valor gravado.
        contadorSegurancaRepository.incrementar(chave, agora, expira);

        return consultar(chave);
    }

    @Override
    @Transactional(readOnly = true)
    public Contagem consultar(String chave) {

        Optional<ContadorSeguranca> registro = contadorSegurancaRepository.findById(chave);

        if (!registro.isPresent()) {
            return Contagem.vazia(Instant.now());
        }

        ContadorSeguranca contador = registro.get();

        // Registro com a janela vencida nao conta: e' como se nao
        // existisse. O registro em si so e apagado pela limpeza.
        if (contador.getExpiraEm().isBefore(LocalDateTime.now())) {
            return Contagem.vazia(Instant.now());
        }

        return new Contagem(
                contador.getContador(),
                contador.getExpiraEm().atZone(ZoneId.systemDefault()).toInstant());
    }

    @Override
    @Transactional
    public int removerPorPrefixo(String prefixo) {
        return contadorSegurancaRepository.removerPorPrefixo(prefixo);
    }

    @Override
    @Transactional
    public int limparExpirados() {
        return contadorSegurancaRepository.removerExpirados(LocalDateTime.now());
    }

    @Override
    @Transactional
    public void limpar() {
        contadorSegurancaRepository.deleteAll();
    }
}
