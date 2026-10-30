package br.com.selecao.locadora.business;

import br.com.selecao.locadora.entity.Auditoria;
import br.com.selecao.locadora.repository.AuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negocio da trilha de auditoria.
 *
 * Cada registro e selado com um HMAC-SHA256 e encadeado no registro
 * anterior. Alterar, forjar ou remover uma linha quebra a corrente, e a
 * sequencia indica onde a ruptura aconteceu.
 *
 * A gravacao roda em transacao separada da operacao de negocio: se as
 * duas dividissem a mesma transacao, uma falha na trilha derrubaria junto
 * o que ja tinha sido gravado.
 */
@Component
public class AuditoriaBO {

    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaBO.class);

    /**
     * Algoritmo do HMAC. O SHA-256 e o mais difusor em hardware hoje.
     */
    private static final String ALGORITMO_HMAC = "HmacSHA256";

    /**
     * Comprimento do hash em hexadecimal (SHA-256 = 64 caracteres).
     */
    private static final int TAMANHO_HASH = 64;

    /**
     * Tamanho da chave HMAC, em bytes. 32 bytes = 256 bits.
     */
    private static final int TAMANHO_CHAVE = 32;

    /**
     * Repositorio da trilha.
     */
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    /**
     * Chave secreta do HMAC, vinda do ambiente.
     *
     * Se nao for informada, e gerada uma chave efemera: o funcionamento
     * esta correto, mas a chave muda a cada reinicio - o que significa
     * que a trilha gravada antes do reinicio nao podera mais ser
     * verificada. Em producao, a variavel DEVE ser definida.
     */
    @Value("${app.security.audit.hmac-key:}")
    private String chaveConfigurada;

    /**
     * Chave efetivamente em uso.
     */
    private byte[] chave;

    /**
     * Indica se a chave foi gerada nesta instancia (efemera).
     */
    private boolean chaveEfemera;

    /**
     * Prepara a chave do HMAC.
     *
     * @throws IllegalStateException se a chave configurada for curta demais
     */
    @PostConstruct
    public void inicializar() {

        if (chaveConfigurada != null && !chaveConfigurada.trim().isEmpty()) {

            chave = chaveConfigurada.getBytes(StandardCharsets.UTF_8);

            if (chave.length < TAMANHO_CHAVE) {
                throw new IllegalStateException(
                        "A chave HMAC da auditoria (" + chave.length + " bytes) e curta demais. "
                                + "Use pelo menos " + TAMANHO_CHAVE + " bytes.");
            }

            chaveEfemera = false;

            LOG.info("AUDITORIA | Chave HMAC carregada do ambiente ({} bytes).", chave.length);
            return;
        }

        // Sem chave no ambiente: gera uma efemera, com aviso claro.
        chave = new byte[TAMANHO_CHAVE];
        new SecureRandom().nextBytes(chave);
        chaveEfemera = true;

        LOG.warn("AUDITORIA | ATENCAO: nenhuma chave HMAC foi informada em "
                + "app.security.audit.hmac-key. Foi gerada uma chave efemera: a trilha "
                + "continua funcionando, mas a verificacao da cadeia so sera valida "
                + "dentro desta mesma instancia da aplicacao.");
    }

    /**
     * Grava um evento na trilha, encadeando-o no ultimo registro.
     *
     * @param usuario     quem fez a operacao
     * @param ipOrigem    endereco de origem
     * @param metodoHttp  metodo HTTP
     * @param caminho     caminho chamado
     * @param statusHttp  codigo de status da resposta
     * @param duracaoMs   duracao da operacao
     * @param acao        acao realizada
     * @param detalhe     detalhe textual (NUNCA o corpo da requisicao)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String usuario,
                           String ipOrigem,
                           String metodoHttp,
                           String caminho,
                           Integer statusHttp,
                           Long duracaoMs,
                           String acao,
                           String detalhe) {

        try {

            // 1) Descobre o ultimo hash da corrente.
            Optional<Auditoria> anterior = auditoriaRepository.findTopByOrderBySequenciaDesc();

            String hashAnterior = anterior.map(Auditoria::getHashAtual).orElse(null);
            long sequencia = anterior.map(a -> a.getSequencia() + 1).orElse(1L);

            // 2) Monta o registro.
            Auditoria registro = new Auditoria();
            registro.setSequencia(sequencia);
            registro.setDataEvento(LocalDateTime.now());
            registro.setUsuario(usuario);
            registro.setIpOrigem(ipOrigem);
            registro.setMetodoHttp(metodoHttp);
            registro.setCaminho(caminho);
            registro.setStatusHttp(statusHttp);
            registro.setDuracaoMs(duracaoMs);
            registro.setAcao(acao);
            registro.setDetalhe(detalhe);
            registro.setHashAnterior(hashAnterior);

            // 3) Calcula o hash deste registro, encadeando no anterior.
            registro.setHashAtual(calcularHash(registro, hashAnterior));

            // 4) Grava.
            auditoriaRepository.save(registro);

        } catch (RuntimeException ex) {
            // A trilha NUNCA pode derrubar a operacao de negocio. Se a
            // gravacao falhar, o problema e registrado no log e a
            // requisicao segue - perder o registro e melhor do que
            // perder a operacao do usuario.
            LOG.error("AUDITORIA | FALHA ao registrar o evento | acao={} caminho={} erro={}",
                    acao, caminho, ex.getMessage(), ex);
        }
    }

    /**
     * Verifica a integridade da cadeia.
     *
     * Percorre os registros em ordem e recalcula o hash de cada um. O
     * primeiro que nao bater e o ponto exato da adulteracao.
     *
     * @param aPartirDe sequencia inicial da verificacao (0 = do comeco)
     * @return o resultado da verificacao
     */
    @Transactional(readOnly = true)
    public ResultadoVerificacao verificar(long aPartirDe) {

        List<Auditoria> registros = auditoriaRepository
                .findBySequenciaGreaterThanEqualOrderBySequenciaAsc(Math.max(aPartirDe, 1L));

        // Sem chave valida, nao ha como verificar nada.
        if (chaveEfemera) {
            return new ResultadoVerificacao(false, 0, 0, null,
                    "A chave HMAC e efemera (gerada nesta instancia). "
                            + "A verificacao so vale dentro desta mesma execucao da aplicacao.");
        }

        if (registros.isEmpty()) {
            return new ResultadoVerificacao(true, 0, 0, null,
                    "A trilha de auditoria esta vazia - nada a verificar.");
        }

        String hashEsperado = registros.get(0).getHashAnterior();
        long verificados = 0;

        for (Auditoria registro : registros) {

            // 1) O encadeamento bate? O hash_anterior deste registro
            //    precisa ser o hash_atual do registro anterior.
            if (!igual(hashEsperado, registro.getHashAnterior())) {
                return new ResultadoVerificacao(false, verificados, registros.size(),
                        registro.getSequencia(),
                        "Cadeia rompida na sequencia " + registro.getSequencia()
                                + ": o encadeamento com o registro anterior nao confere.");
            }

            // 2) O conteudo bate com o hash gravado?
            String hashRecalculado = calcularHash(registro, registro.getHashAnterior());

            if (!igual(hashRecalculado, registro.getHashAtual())) {
                return new ResultadoVerificacao(false, verificados, registros.size(),
                        registro.getSequencia(),
                        "Registro adulterado na sequencia " + registro.getSequencia()
                                + ": o conteudo gravado nao corresponde ao hash selado.");
            }

            hashEsperado = registro.getHashAtual();
            verificados++;
        }

        // 3) A sequencia tem buracos? (detecta remocao de registro)
        long maiorSequencia = auditoriaRepository.obterMaiorSequencia();
        long total = registros.size();

        if (aPartirDe <= 1 && maiorSequencia > total) {
            return new ResultadoVerificacao(false, verificados, total, maiorSequencia,
                    "Ha buraco na sequencia: o ultimo registro e " + maiorSequencia
                            + ", mas existem apenas " + total + " registros. "
                            + "Isso indica remocao de registro.");
        }

        return new ResultadoVerificacao(true, verificados, total, maiorSequencia,
                "A cadeia de auditoria esta integra: " + verificados + " registro(s) verificado(s).");
    }

    /**
     * Calcula o hash selado de um registro.
     *
     * O conteudo canonico concatena os campos relevantes em uma ordem
     * FIXA. A ordem precisa ser estavel: se mudasse, a verificacao
     * acusaria adulteracao em tudo.
     *
     * @param registro     o registro a selar
     * @param hashAnterior o hash do registro anterior da cadeia
     * @return o hash, em hexadecimal
     */
    private String calcularHash(Auditoria registro, String hashAnterior) {

        String conteudo = String.join("|",
                String.valueOf(registro.getSequencia()),
                registro.getDataEvento() == null ? "" : registro.getDataEvento().toString(),
                registro.getUsuario() == null ? "" : registro.getUsuario(),
                registro.getIpOrigem() == null ? "" : registro.getIpOrigem(),
                registro.getMetodoHttp() == null ? "" : registro.getMetodoHttp(),
                registro.getCaminho() == null ? "" : registro.getCaminho(),
                registro.getStatusHttp() == null ? "" : String.valueOf(registro.getStatusHttp()),
                registro.getDuracaoMs() == null ? "" : String.valueOf(registro.getDuracaoMs()),
                registro.getAcao() == null ? "" : registro.getAcao(),
                registro.getDetalhe() == null ? "" : registro.getDetalhe(),
                hashAnterior == null ? "" : hashAnterior);

        try {

            Mac hmac = Mac.getInstance(ALGORITMO_HMAC);
            hmac.init(new SecretKeySpec(chave, ALGORITMO_HMAC));

            byte[] bytes = hmac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexadecimal = new StringBuilder(TAMANHO_HASH);
            for (byte b : bytes) {
                hexadecimal.append(String.format("%02x", b));
            }

            return hexadecimal.toString();

        } catch (java.security.GeneralSecurityException ex) {
            // Nao ha o que fazer se o algoritmo HMAC nao existir: e um
            // erro de plataforma, nao de negocio.
            throw new IllegalStateException("Nao foi possivel calcular o HMAC da trilha de auditoria.", ex);
        }
    }

    /**
     * Compara dois hashes semieszacao de tempo (evita o ataque de tempo).
     */
    private boolean igual(String a, String b) {

        if (a == null || b == null) {
            return a == null && b == null;
        }

        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resultado da verificacao da cadeia.
     */
    public static class ResultadoVerificacao {

        private final boolean integra;
        private final long verificados;
        private final long total;
        private final Long sequenciaRompida;
        private final String mensagem;

        public ResultadoVerificacao(boolean integra, long verificados, long total,
                                    Long sequenciaRompida, String mensagem) {
            this.integra = integra;
            this.verificados = verificados;
            this.total = total;
            this.sequenciaRompida = sequenciaRompida;
            this.mensagem = mensagem;
        }

        public boolean isIntegra() {
            return integra;
        }

        public long getVerificados() {
            return verificados;
        }

        public long getTotal() {
            return total;
        }

        public Long getSequenciaRompida() {
            return sequenciaRompida;
        }

        public String getMensagem() {
            return mensagem;
        }
    }
}
