/**
 * Renovacao automatica do token de acesso.
 *
 * O token de acesso expira em poucos minutos (15 por padrao). Ate o
 * frontend do projeto usava o endpoint "/auth/refresh", que existia e
 * funcionava - mas nunca era chamado. O resultado:
 *
 *   1. o token expirava;
 *   2. a proxima requisicao voltava 401;
 *   3. o interceptor limpava a sessao e abria a tela de login.
 *
 * Ou seja: quem deixava a aba aberta por mais de 15 minutos era
 * expulso do sistema, sem ter feito nada de errado.
 *
 * Este modulo resolve isso da forma certa: quando uma requisicao volta
 * 401 E existe token de renovacao, ele troca o token de acesso por um
 * novo e REFAZ a requisicao. A pessoa nao ve nada - a sessao continua.
 *
 * A renovacao acontece uma vez por vez. Se duas requisicoes voltarem 401
 * juntas, a segunda espera a primeira terminar (a promessa "renovando")
 * e reaproveita o token novo.
 *
 * IMPORTANTE - por que este arquivo nao importa "./autenticacao":
 *
 * Os tres arquivos de servico (api, autenticacao e renovacao) formam
 * um ciclo de imports se cada um precisar do outro:
 *
 *     api -> renovacao -> autenticacao -> api
 *
 * O webpack tolera o ciclo, mas a ordem de resolucao deixa um dos
 * modulos com o import ainda indefinido no momento do uso - e o
 * primeiro sintoma e exatamente o que estava acontecendo: o
 * "confirmarSessao" rodava sem fazer nenhuma requisicao (rede vazia), e
 * a tela de login nunca abria.
 *
 * A solucao e este arquivo nao depender de "./autenticacao". Ele usa o
 * axios direto, com a mesma instancia, e monta a chamada de renovacao
 * aqui. A duplicacao e' de uma linha - e o que mantem o grafo de
 * imports sem ciclos.
 */
import axios from 'axios';
import store from '../store';

// URL base da API. Vem do .env (VUE_APP_API_BASE_URL); o fallback e o
// endereco local padrao. Mesma regra do arquivo "api.js".
const baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081';

/**
 * A promessa da renovacao em andamento.
 *
 * Enquanto ela existe, qualquer chamada que chegar espera. E' o que
 * evita que N requisicoes fiquem renovando o token ao mesmo tempo (o que
 * revogaria a sessao: cada renovacao invalida o token anterior).
 */
let renovando = null;

/**
 * Troca o token de acesso por um novo.
 *
 * @returns {Promise<string|null>} o token novo, ou null se nao deu
 */
function renovarToken() {
  const tokenRenovacao = store.state.tokenRenovacao;

  if (!tokenRenovacao) {
    return Promise.resolve(null);
  }

  // A chamada vai pelo axios direto, e NAO pela instancia de "api.js".
  //
  // Isso e' deliberado: se passasse pela instancia, o interceptor de
  // requisicao dela anexaria o "Authorization: Bearer <token vencido>"
  // no cabecalho - e o filtro de JWT da API recusa o token antes mesmo
  // de o "/auth/refresh" ser avaliado. O "permitAll" da rota nao
  // salva: o filtro roda antes da autorizacao.
  //
  // O token de renovacao vai no CORPO da requisicao, e e' ele que a API
  // le. O cabecalho aqui so' atrapalharia.
  return axios.post(
    baseURL + '/auth/refresh',
    { tokenRenovacao: tokenRenovacao },
    {
      timeout: 15000,
      headers: { 'Content-Type': 'application/json' }
    }
  )
    .then((r) => {
      const resposta = r.data;

      if (!resposta || !resposta.tokenAcesso) {
        return null;
      }

      store.commit('TROCAR_TOKEN_ACESSO', resposta.tokenAcesso);

      // A renovacao tambem devolve um token de renovacao novo (a
      // rotacao). Guardar evita que o token velho, ja invalidado, seja
      // usado na proxima renovacao.
      if (resposta.tokenRenovacao) {
        store.commit('TROCAR_TOKEN_RENOVACAO', resposta.tokenRenovacao);
      }

      return resposta.tokenAcesso;
    })
    .catch(() => {
      // A renovacao falhou: o token de renovacao expirou ou foi
      // revogado. A sessao acabou de verdade - o interceptor de
      // resposta cuida de limpar o estado e abrir o login.
      //
      // O motivo da falha nao e' repassado: quem trata o 401 e' o
      // interceptor, e o que importa aqui e' apenas "nao ha token
      // novo".
      return null;
    });
}

/**
 * Devolve a promessa da renovacao em andamento, ou cria uma.
 *
 * E' o que garante que apenas uma renovacao acontece por vez.
 *
 * @returns {Promise<string|null>} o token novo
 */
export function renovarSeNecessario() {
  if (!renovando) {
    renovando = renovarToken().then((token) => {
      // Libera o cadeado para a proxima renovacao.
      renovando = null;
      return token;
    });
  }

  return renovando;
}
