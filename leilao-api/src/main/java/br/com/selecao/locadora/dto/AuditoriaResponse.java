package br.com.selecao.locadora.dto;

import java.time.LocalDateTime;

/**
 * ============================================================================
 *  DTO: REGISTRO DA TRILHA DE AUDITORIA (forma de saida da API)
 * ============================================================================
 *
 *  E a MESMA informacao da entidade, mas sem os campos internos da cadeia
 *  de hash. Por que? Porque o hash e o mecanismo de prova: expor o
 *  hash_atual permitiria a um cliente tentar forjar registros. A API
 *  mostra o QUE aconteceu; a PROVA fica no servidor.
 * ============================================================================
 */
public class AuditoriaResponse {

    private Long id;
    private Long sequencia;
    private LocalDateTime dataEvento;
    private String usuario;
    private String ipOrigem;
    private String metodoHttp;
    private String caminho;
    private Integer statusHttp;
    private Long duracaoMs;
    private String acao;
    private String detalhe;

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
}
