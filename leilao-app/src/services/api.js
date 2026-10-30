/**
 * Comunicacao com a API.
 *
 * Se cada tela chamasse o axios direto, a URL da API, o tempo limite e o
 * tratamento de erro estariam espalhados por N arquivos. Aqui ficam em um
 * lugar so.
 *
 * Nao ha segredo neste arquivo: o frontend roda no navegador do usuario, e
 * qualquer coisa escrita nele e publica.
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
