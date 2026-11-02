/**
 * Comunicacao com a API.
 *
 * Se cada tela chamasse o axios direto, a URL da API, o timeout, o
 * cabecalho de autenticacao e o tratamento de erro ficariam espalhados
 * por N arquivos. Aqui ficam todos em um lugar so.
 *
 * Nada de segredo neste arquivo: o frontend roda no navegador do usuario,
 * e qualquer coisa escrita nele e publica.
 */
import axios from 'axios';
import store from '../store';
import { renovarSeNecessario } from './renovacao';

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
 * Interceptor de REQUISICAO: roda antes de cada chamada sair.
 *
 * E' aqui que o token de acesso entra no cabecalho "Authorization".
 *
 * Por que um interceptor, e nao o token no corpo de cada chamada?
 *
 * Porque a sessao e uma propriedade da aplicacao, nao de uma tela. Se
 * cada tela precisasse lembrar de anexar o token, a esqueceria seria
 * certa - e o resultado seria um 401 que ninguem entenderia. O
 * interceptor garante que nenhuma requisicao saia sem o token, sem
 * depender de quem a disparou.
 */
api.interceptors.request.use(config => {
  const token = store.state.tokenAcesso;

  // O logout nao leva o "Authorization".
  //
  // Ele nao precisa: o token de renovacao vai no corpo da requisicao,
  // e e' ele que a API le. O cabecalho so' atrapalharia - e, se o token
  // de acesso ja' tivesse vencido, o filtro de JWT da API recusaria a
  // chamada antes do "/auth/logout" ser avaliado (o "permitAll" da rota
  // nao salva: o filtro roda antes da autorizacao).
  //
  // A renovacao tambem nao passa por aqui: ela e' feita em
  // "services/renovacao.js", direto no axios, justamente para nao ter
  // esse conflito de cabecalho (e para nao criar um ciclo de imports).
  if (/^\/(auth|autenticacao)\/logout/.test(config.url || '')) {
    delete config.headers.Authorization;
    return config;
  }

  // O token so vai quando existe. No perfil de desenvolvimento (a
  // autenticacao desligada), nao ha token e a API aceita a chamada
  // assim mesmo.
  if (token) {
    config.headers.Authorization = 'Bearer ' + token;
  }

  return config;
});

/**
 * Interceptor de RESPOSTA: roda quando a API respondeu (com sucesso ou com
 * erro). Aqui a gente desempacota o formato de erro do backend.
 */
api.interceptors.response.use(
  // Sucesso: devolve os dados direto (response.data).
  response => response.data,

  // Erro: a API respondeu com status 4xx/5xx.
  async error => {
    const resposta = error.response;

    // Sem resposta (API fora do ar, DNS, CORS bloqueado) o erro nao tem
    // "response". Nesse caso montamos uma mensagem util.
    if (!resposta) {
      return Promise.reject({
        status: 0,
        mensagem: 'Não foi possível conectar à API. Verifique se o servidor está rodando '
          + 'na porta ' + (new URL(baseURL).port || '80') + ' e se a API foi iniciada.'
      });
    }

    // Token expirado: a sessao pode ter sobrevivido.
    //
    // O token de acesso expira em poucos minutos, mas o token de
    // renovacao dura mais. Quando o acesso expira e ainda ha renovacao
    // guardada, a sessao nao acabou - so' o token de acesso venceu. A
    // solucao e trocar o token e refazer a requisicao, sem que a pessoa
    // perceba nada.
    //
    // As condicoes para renovar:
    //
    //   - o status e' 401 (credencial recusada);
    //   - ha token de renovacao guardado.
    //
    // O "/auth/me" RENOVA, e e' o que faz o F5 funcionar: ao recarregar
    // a pagina com o token de acesso vencido, a consulta descobre que
    // ele nao vale, troca por um novo e segue - em vez de expulsar a
    // pessoa para a tela de login.
    //
    // EXCECAO: o logout nunca renova.
    //
    // O logout e' a unica chamada em que o token e' removido de
    // proposito (veja o interceptor de requisicao). Se ele voltasse 401
    // e o interceptor tentasse renovar, teriamos um laco: 401 renova,
    // a renovacao devolve 401, renova de novo... Cada volta consome
    // uma vaga do limite de taxa, e o usuario ve o console enchendo de
    // erro. Alem disso, o logout nao faz sentido com token novo: o
    // que se quer e' revogar a sessao atual.
    //
    // O logout trata o erro la (o "catch" do componente), e limpa a
    // sessao local de qualquer jeito - que e' o que o usuario pediu.
    const ehLogout = /^\/(auth|autenticacao)\/logout/.test((error.config && error.config.url) || '');

    if (resposta.status === 401
      && store.state.tokenAcesso
      && store.state.tokenRenovacao
      && !ehLogout) {

      // "renovarSeNecessario" devolve a promessa da renovacao em
      // andamento, ou cria uma. Se duas requisicoes voltaram 401
      // juntas, a segunda pega a promessa da primeira - e nao renova de
      // novo.
      //
      // Isso e' obrigatorio: a API rotaciona o token de renovacao
      // (apresentar o mesmo token duas vezes e' detectado como reuse, e
      // derruba a sessao inteira).
      const token = await renovarSeNecessario();

      if (token) {
        error.config.headers.Authorization = 'Bearer ' + token;
        return api(error.config);
      }
    }

    // Token expirado ou invalido, e nao ha como renovar: a sessao
    // acabou de verdade. A gente limpa a sessao local e devolve 401,
    // para que o roteador mande o usuario para a tela de login.
    //
    // O "401" so' e' tratado aqui quando a API esta com a autenticacao
    // ligada. No perfil de desenvolvimento (desligada), um 401 significa
    // outra coisa - e o logout local nao-help-nem-harms, mas vale
    // manter o comportamento previsivel.
    //
    // No logout, este bloco NAO roda: quem chamou (o componente) ja'
    // limpa a sessao local, e limpar duas vezes abriria a tela de
    // login no meio do logout - o que parece um defeito para quem
    // esta' tentando sair.
    if (resposta.status === 401 && store.state.tokenAcesso && !ehLogout) {
      store.commit('GUARDAR_TOKENS', {});
      store.commit('GUARDAR_USUARIO', null);
      store.commit('ABRIR_LOGIN', true);
    }

    // Erro de validacao de campos (HTTP 400): devolvemos a lista por campo.
    if (resposta.status === 400 && resposta.data && resposta.data.campos) {
      return Promise.reject({
        status: 400,
        mensagem: resposta.data.mensagem || 'Dados inválidos.',
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