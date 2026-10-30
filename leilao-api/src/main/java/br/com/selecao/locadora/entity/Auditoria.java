package br.com.selecao.locadora.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * Registro da trilha de auditoria.
 *
 * Cada linha guarda o HMAC-SHA256 do conteudo junto com o hash da linha
 * anterior: e uma corrente. Alterar, forjar ou remover uma linha quebra a
 * corrente, e a verificacao aponta a sequencia da ruptura.
 *
 * A sequencia e separada do id porque o id pode ser reatribuido se alguem
 * apagar uma linha - a sequencia nao.
 */
@Entity
@Table(name = "tb_auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Numero monotonico e inquebravel da trilha.
     *
     * E o que permite detectar a remocao de um registro: os ids podem ser
     * reatribuidos, mas a sequencia nunca retrocede.
     */
    @Column(name = "sequencia", nullable = false)
    private Long sequencia;

    /**
     * Instante do evento.
     */
    @Column(name = "data_evento", nullable = false)
    private LocalDateTime dataEvento;

    /**
     * Quem executou a operacao (null ou "anonimo" quando nao ha login).
     */
    @Column(name = "usuario", length = 100)
    private String usuario;

    /**
     * Endereco IP de origem.
     */
    @Column(name = "ip_origem", length = 45)
    private String ipOrigem;

    /**
     * Metodo HTTP (POST, PUT, PATCH, DELETE).
     */
    @Column(name = "metodo_http", length = 10)
    private String metodoHttp;

    /**
     * Caminho da URL chamada.
     */
    @Column(name = "caminho", length = 500)
    private String caminho;

    /**
     * Codigo de status da resposta (201, 204, 409...).
     */
    @Column(name = "status_http")
    private Integer statusHttp;

    /**
     * Duracao da operacao, em milissegundos.
     */
    @Column(name = "duracao_ms")
    private Long duracaoMs;

    /**
     * Acao registrada ("CRIAR", "ALTERAR", "EXCLUIR"...).
     */
    @Column(name = "acao", length = 100)
    private String acao;

    /**
     * Detalhe textual da operacao.
     *
     * Nunca guarda o corpo da requisicao. O cadastro de empresa recebe a
     * senha no corpo - registrar o corpo colocaria a senha em texto puro
     * na trilha de auditoria.
     */
    @Column(name = "detalhe", columnDefinition = "TEXT")
    private String detalhe;

    /**
     * Hash do registro anterior da cadeia (null no primeiro registro).
     */
    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    /**
     * Hash deste registro - o elo seguinte da corrente.
     */
    @Column(name = "hash_atual", nullable = false, length = 64)
    private String hashAtual;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSequencia() {
        return sequencia;
    }

    public void setSequencia(Long sequencia) {
        this.sequencia = sequencia;
    }

    public LocalDateTime getDataEvento() {
        return dataEvento;
    }

    public void setDataEvento(LocalDateTime dataEvento) {
        this.dataEvento = dataEvento;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getIpOrigem() {
        return ipOrigem;
    }

    public void setIpOrigem(String ipOrigem) {
        this.ipOrigem = ipOrigem;
    }

    public String getMetodoHttp() {
        return metodoHttp;
    }

    public void setMetodoHttp(String metodoHttp) {
        this.metodoHttp = metodoHttp;
    }

    public String getCaminho() {
        return caminho;
    }

    public void setCaminho(String caminho) {
        this.caminho = caminho;
    }

    public Integer getStatusHttp() {
        return statusHttp;
    }

    public void setStatusHttp(Integer statusHttp) {
        this.statusHttp = statusHttp;
    }

    public Long getDuracaoMs() {
        return duracaoMs;
    }

    public void setDuracaoMs(Long duracaoMs) {
        this.duracaoMs = duracaoMs;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }

    public String getHashAnterior() {
        return hashAnterior;
    }

    public void setHashAnterior(String hashAnterior) {
        this.hashAnterior = hashAnterior;
    }

    public String getHashAtual() {
        return hashAtual;
    }

    public void setHashAtual(String hashAtual) {
        this.hashAtual = hashAtual;
    }
}
