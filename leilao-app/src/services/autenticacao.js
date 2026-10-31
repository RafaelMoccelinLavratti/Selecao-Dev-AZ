/**
 * Servico de autenticacao.
 *
 * Concentra as quatro chamadas de sessao da API:
 *
 *   POST /auth/login    - entra e recebe o par de tokens
 *   POST /auth/refresh  - troca o token de renovacao por um acesso novo
 *   POST /auth/logout   - encerra a sessao (revoga no servidor)
 *   GET  /auth/me       - quem esta autenticado
 *
 * A regra de ouro do frontend: o token NUNCA aparece no codigo que o
 * usuario ve. Ele fica no localStorage, gerenciado pela store do Vuex.
 * Esta camada so pega o que a API devolve e entrega pra store guardar.
 */
import api from './api';

/**
 * Entra na aplicacao.
 *
 * @param {string} usuario nome de usuario
 * @param {string} senha   senha
 * @returns {object} os tokens e os dados do usuario
 */
export async function entrar(usuario, senha) {
  const resposta = await api.post('/auth/login', { usuario, senha });
  return resposta;
}

/**
 * Consulta quem esta autenticado.
 *
 * Serve para confirmar a sessao ao abrir a aplicacao: o token pode ter
 * expirado no servidor, e so' a API sabe.
 *
 * @returns {object} { usuario, autenticado, papel, empresaId, administrador }
 */
export async function quemSou() {
  return api.get('/auth/me');
}

/**
 * Encerra a sessao.
 *
 * O token de renovacao e' enviado para a API, que revoga a familia
 * inteira de tokens. Apos isso, o token de acesso deixa de valer na
 * hora - sem esperar os 15 minutos de expiracao.
 *
 * O nome da funcao e' "encerrarSessao" (e nao "sair") para nao
 * colidir com o metodo "sair" do componente que a chama - nomes
 * iguais no mesmo escopo causariam uma chamada recursiva sem fim.
 *
 * @param {string} tokenRenovacao o token de renovacao da sessao
 * @returns {object} a resposta da API
 */
export async function encerrarSessao(tokenRenovacao) {
  return api.post('/auth/logout', { tokenRenovacao });
}

/**
 * Renova o token de acesso.
 *
 * @param {string} tokenRenovacao o token de renovacao da sessao
 * @returns {object} o par novo de tokens
 */
export async function renovar(tokenRenovacao) {
  return api.post('/auth/refresh', { tokenRenovacao });
}
