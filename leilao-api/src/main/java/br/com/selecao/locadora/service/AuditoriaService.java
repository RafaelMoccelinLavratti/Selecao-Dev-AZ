package br.com.selecao.locadora.service;

import br.com.selecao.locadora.business.AuditoriaBO;
import br.com.selecao.locadora.dto.AuditoriaResponse;
import br.com.selecao.locadora.entity.Auditoria;
import br.com.selecao.locadora.repository.AuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller REST da trilha de auditoria.
 *
 * GET /auditoria            - lista os registros (mais recentes primeiro)
 * GET /auditoria/verificar  - verifica a integridade da corrente
 *
 * A verificacao e um endpoint e nao um job porque responde a uma pergunta
 * de investigacao: "alguem mexeu na trilha?".
 */
@RestController
@RequestMapping("/auditoria")
public class AuditoriaService {

    /**
     * Numero maximo de registros devolvidos em uma pagina.
     */
    private static final int TAMANHO_MAXIMO_PAGINA = 200;

    /**
     * Numero maximo de registros que a verificacao processa de uma vez.
     */
    private static final int LIMITE_VERIFICACAO = 10_000;

    /**
     * Camada de negocio com as regras da trilha.
     */
    @Autowired
    private AuditoriaBO auditoriaBO;

    /**
     * Repositorio, usado apenas para a leitura paginada.
     */
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    /**
     * GET /auditoria - lista os registros da trilha.
     *
     * @param limite    numero maximo de registros (padrao 50, maximo 200)
     * @param usuario   filtra pelos registros de um usuario especifico
     * @return a lista de registros, do mais recente para o mais antigo
     */
    @GetMapping
    public ResponseEntity<List<AuditoriaResponse>> listar(
            @RequestParam(defaultValue = "50") int limite,
            @RequestParam(required = false) String usuario) {

        int tamanho = Math.min(Math.max(limite, 1), TAMANHO_MAXIMO_PAGINA);

        List<Auditoria> registros;

        if (usuario != null && !usuario.trim().isEmpty()) {
            registros = auditoriaRepository.findByUsuarioOrderBySequenciaDesc(
                    usuario.trim(), PageRequest.of(0, tamanho));
        } else {
            registros = auditoriaRepository.findAllByOrderBySequenciaDesc(
                    PageRequest.of(0, tamanho));
        }

        List<AuditoriaResponse> resposta = registros.stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(resposta);
    }

    /**
     * GET /auditoria/verificar - verifica a integridade da cadeia.
     *
     * @param aPartirDe sequencia inicial da verificacao (padrao: do comeco)
     * @return o resultado, com o veredito e o ponto da eventual ruptura
     */
    @GetMapping("/verificar")
    public ResponseEntity<Map<String, Object>> verificar(
            @RequestParam(defaultValue = "1") long aPartirDe) {

        AuditoriaBO.ResultadoVerificacao resultado = auditoriaBO.verificar(aPartirDe);

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("integra", resultado.isIntegra());
        corpo.put("registrosVerificados", resultado.getVerificados());
        corpo.put("totalAnalisado", resultado.getTotal());
        corpo.put("ultimaSequencia", resultado.getSequenciaRompida());
        corpo.put("mensagem", resultado.getMensagem());

        // 200 quando a cadeia esta integra; 409 (conflito) quando ha
        // ruptura - o codigo reflete o estado real, e nao o 200 "de
        // fachada" que esconderia o problema.
        return ResponseEntity
                .status(resultado.isIntegra()
                        ? org.springframework.http.HttpStatus.OK
                        : org.springframework.http.HttpStatus.CONFLICT)
                .body(corpo);
    }

    /**
     * Converte a entidade no DTO de resposta.
     *
     * Repare que os hashes NAO sao copiados. Eles sao o mecanismo de
     * prova da trilha e nao precisam (nao devem) sair da API.
     */
    private AuditoriaResponse paraResponse(Auditoria registro) {

        AuditoriaResponse dto = new AuditoriaResponse();
        dto.setId(registro.getId());
        dto.setSequencia(registro.getSequencia());
        dto.setDataEvento(registro.getDataEvento());
        dto.setUsuario(registro.getUsuario());
        dto.setIpOrigem(registro.getIpOrigem());
        dto.setMetodoHttp(registro.getMetodoHttp());
        dto.setCaminho(registro.getCaminho());
        dto.setStatusHttp(registro.getStatusHttp());
        dto.setDuracaoMs(registro.getDuracaoMs());
        dto.setAcao(registro.getAcao());
        dto.setDetalhe(registro.getDetalhe());

        return dto;
    }
}
