/**
 * ============================================================================
 *  SERVICO DE COMUNICACAO COM A API (src/services/api.js)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  POR QUE CENTRALIZAR AS CHAMADAS HTTP EM UM SO LUGAR?
 *  ------------------------------------------------------
 *  Se cada tela chamasse o axios direto, a URL da API, o tratamento de
 *  erro e o tempo limite estariam espalhados por N arquivos. Mudar a
 *  porta da API seria uma edicao em 5 lugares.
 *
 *  Aqui ha UM unico ponto de contato com o backend. Se a API mudar de
 *  endereco, muda-se uma linha (a variavel de ambiente).
 *
 *  ------------------------------------------------------
 *  TRATAMENTO DE ERRO CENTRALIZADO
 *  ------------------------------------------------------
 *  O backend responde os erros sempre no mesmo formato (ver
 *  GlobalExceptionHandler no projeto Java):
 *      { "status": 400, "erro": "...", "mensagem": "...", "campos": {...} }
 *
 *  O interceptor abaixo traduz esse formato em um objeto simples, para
 *  que a tela mostre a mensagem certa sem precisar entender o formato
 *  do erro.
 *
 *  ------------------------------------------------------
 *  SEGURANCA
 *  ------------------------------------------------------
 *  - Nao ha segredo (token, senha) no codigo do frontend: o frontend
 *    roda no navegador do usuario e QUALQUER coisa escrita nele pode
 *    ser lida por qualquer pessoa. Segredo no frontend e seguranca de
 *    papel - nao existe.
 *  - O "timeout" evita que a tela fique travada para sempre se a API
 *    estiver fora do ar.
 * ============================================================================
 */
import axios from 'axios';

/**
 * URL base da API.
 * Vem da variavel de ambiente VUE_APP_API_BASE_URL (definida no arquivo
 * .env). O fallback e o endereco local padrao.
 */
const baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081';

/**
 * Instancia do axios (o "carro" que faz as requisicoes HTTP).
 */
const api = axios.create({
  baseURL: baseURL,
  timeout: 15000, // 15 segundos: tempo maximo de espera por resposta
  headers: {
    'Content-Type': 'application/json'
  }
});

/**
 * Interceptor de RESPOSTA: roda quando a API respondeu (com sucesso ou
 * com erro). Aqui "desempacotamos" o formato de erro do backend.
 */
api.interceptors.response.use(
  // Sucesso: devolve os dados direto (response.data).
  response => response.data,

  // Erro: a API respondeu com status 4xx/5xx.
  error => {
    const resposta = error.response;

    // Se NAO houve resposta (API fora do ar, DNS, CORS bloqueado),
    // o erro nao tem "response". Nesse caso montamos uma mensagem util.
    if (!resposta) {
      return Promise.reject({
        status: 0,
        mensagem: 'Nao foi possivel conectar a API. Verifique se o servidor esta rodando '
          + 'na porta ' + (new URL(baseURL).port || '80') + ' e se a API foi iniciada.'
      });
    }

    // Erro de validacao de campos (HTTP 400): devolvemos a lista por campo.
    if (resposta.status === 400 && resposta.data && resposta.data.campos) {
      return Promise.reject({
        status: 400,
        mensagem: resposta.data.mensagem || 'Dados invalidos.',
        campos: resposta.data.campos
      });
    }

    // Demais erros: usamos a mensagem que o backend preparou.
    return Promise.reject({
      status: resposta.status,
      mensagem: (resposta.data && resposta.data.mensagem)
        ? resposta.data.mensagem
        : 'Erro ao comunicar com a API (HTTP ' + resposta.status + ').'
    });
  }
);

export default api;
