package br.com.selecao.locadora.config;

import java.time.Duration;

/**
 * Onde os contadores de seguranca moram.
 *
 * Os dois controles que dependem de contagem - o anti-forca-bruta e o
 * limitador de requisicoes - precisam responder a mesma pergunta: "quantas
 * vezes isso aconteceu, dentro da janela?".
 *
 * A implementacao padrao guarda a resposta na memoria do processo. E
 * rapida e nao depende de nada, mas tem um limite importante: o contador
 * vale so para a instancia que o guarda. Com duas replicas da API atras
 * do balanceador, cada uma tem o seu contador, e o limite efetivo por IP
 * passa a ser o dobro. O atacante ainda teria como distribuir as
 * tentativas entre as replicas.
 *
 * A implementacao em banco resolve isso: o contador e compartilhado, e
 * vale para a API inteira. O preco e' uma ida ao banco a cada
 * requisicao.
 *
 * Qual usar depende de quantas replicas tem:
 *
 *   - uma replica (desenvolvimento, avaliacao): memoria. E mais rapido
 *     e nao exige nenhuma configuracao extra.
 *   - varias replicas (producao): banco.
 *
 * A escolha e' feita em app.security.rate-limit.store.
 */
public interface ContadorStore {

    /**
     * Registra mais uma ocorrencia e devolve a contagem atual.
     *
     * A janela e'renewed a cada ocorrencia: e assim que o controle
     * deslizante funciona - o atacante tem 5 tentativas a cada 15
     * minutos, e nao 5 no total para sempre.
     *
     * @param chave  identificador do contador
     * @param janela tamanho da janela
     * @return a contagem depois do incremento
     */
    Contagem incrementar(String chave, Duration janela);

    /**
     * Le a contagem atual, sem alterar nada.
     *
     * @param chave identificador do contador
     * @return a contagem; zerada se nao houver registro valido
     */
    Contagem consultar(String chave);

    /**
     * Remove os registros cuja janela ja venceu.
     *
     * @return quantos registros foram removidos
     */
    int limparExpirados();

    /**
     * Remove todos os contadores cuja chave comeca com o prefixo.
     *
     * Existe para o caso de um login bem-sucedido: o contador do
     * usuario e' zerado em todos os IPs de uma vez, porque quem acabou
     * de provar que sabe a senha nao precisa ficar preso por tentativas
     * antigas, nem por tentativas vindas de outro lugar.
     *
     * O prefixo e' comparado como texto, sem interpretacao de curingas:
     * um nome de usuario com "%" ou "_" nao pode casar com o contador de
     * outro usuario.
     *
     * @param prefixo inicio da chave
     * @return quantos contadores foram removidos
     */
    int removerPorPrefixo(String prefixo);

    /**
     * Remove todos os registros.
     *
     * Existe para os testes, que precisam comecar do zero.
     */
    void limpar();
}
