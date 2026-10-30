/**
 * Comunicacao com a API.
 *
 * Se cada tela chamasse o axios direto, a URL da API, o timeout e o
 * tratamento de erro ficariam espalhados por N arquivos. Aqui ficam
 * todos em um lugar so.
 *
 * Nada de segredo neste arquivo: o frontend roda no navegador do usuario,
 * e qualquer coisa escrita nele e publica.
 */
import axios from 'axios';

// URL base da API. Vem do .env (VUE_APP_API_BASE_URL); o fallback e o
// endereco local padrao.
const baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081';

// Instancia do axios.
const api = axios.create({
  baseURL: baseURL,
  timeout: 15000, // 15 segundos: tempo maximo de espera por resposta
  headers: {
    'Content-Type': 'application/json'
  }
});

/**
 * Interceptor de RESPOSTA: roda quando a API respondeu (com sucesso ou com
 * erro). Aqui a gente desempacota o formato de erro do backend.
 */
api.interceptors.response.use(
  // Sucesso: devolve os dados direto (response.data).
  response => response.data,

  // Erro: a API respondeu com status 4xx/5xx.
  error => {
    const resposta = error.response;

    // Sem resposta (API fora do ar, DNS, CORS bloqueado) o erro nao tem
    // "response". Nesse caso montamos uma mensagem util.
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
