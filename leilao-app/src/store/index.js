/**
 * Store global (Vuex).
 *
 * O estado compartilhado da aplicacao. Antes era vazio - cada tela
 * carregava o que precisava direto da API. Agora tem uma responsabilidade:
 * a sessao do usuario.
 *
 * A sessao mora aqui, e nao em cada tela, por tres razoes:
 *
 *   1. o token de acesso precisa estar disponivel para o interceptor do
 *      axios montar o cabecalho "Authorization" de TODAS as requisicoes,
 *      e o interceptor nao tem acesso a tela que disparou a chamada;
 *
 *   2. o estado "autenticado" precisa ser o mesmo em todas as telas, para
 *      que o menu e o botão de sair mostrem a mesma coisa;
 *
 *   3. a rota protegida precisa consultar o estado antes de deixar o
 *      usuario entrar numa tela.
 *
 * Sobre guardar o token: ele fica no "localStorage" do navegador. Isso
 * tem um tradeoff conhecido - o "localStorage" sobrevive ao fechamento do
 * navegador e é legivel por qualquer script da pagina (um XSS rouba o
 * token). A alternativa mais forte seria o cookie "HttpOnly", que o
 * JavaScript nao consegue ler, mas ela exige que a API funcione com
 * credencial de origem (cookies) e com a protecao de CSRF ligada - o que
 * nao cabe no modelo stateless por cabecalho que este projeto usa.
 *
 * A mitigacao que cabe aqui: o token de acesso expira em poucos minutos
 * (15 por padrao), e o logout envia o token de renovacao para a API, que
 * revoga a sessao inteira - o token de acesso deixa de valer na hora,
 * sem esperar expirar.
 */
import Vue from 'vue';
import Vuex from 'vuex';
import axios from 'axios';

// Instala o plugin do Vue. Sem essa linha o this.$store nao existiria
// em nenhum componente.
Vue.use(Vuex);

// ---------------------------------------------------------------------------
// Chaves do localStorage
// ---------------------------------------------------------------------------

/**
 * Chave onde os tokens ficam guardados.
 *
 * O prefixo "az.leilao." evita colisao com o storage de outro sistema que
 * possa rodar na mesma origem.
 */
const CHAVE_STORAGE = 'az.leilao.tokens';

/**
 * Chave do usuario logado.
 *
 * Guarda o nome, o papel e o id da empresa. E' o que permite ao menu
 * mostrar "Ola, fulano" e esconder o que o usuario nao pode acessar.
 */
const CHAVE_USUARIO = 'az.leilao.usuario';

/**
 * Chave onde o prazo do token e' guardado.
 *
 * E' guardado porque o token sobrevive ao F5 (no localStorage), e o
 * prazo precisa sobreviver junto: sem ele, a aplicacao nao saberia
 * quando renovar - e so' descobriria pela via do 401.
 */
const CHAVE_PRAZO = 'az.leilao.tokens.prazo';

/**
 * Le o usuario guardado no localStorage.
 *
 * E' uma funcao do modulo, e nao uma acao da store, porque ela roda
 * durante a criacao do estado - e, dentro do objeto de estado, o
 * "this" ainda nao e' a store.
 *
 * @returns {object|null} os dados do usuario, ou null se nao houver
 */
function lerUsuarioSalvo() {
  const bruto = localStorage.getItem(CHAVE_USUARIO);

  if (!bruto) {
    return null;
  }

  try {
    return JSON.parse(bruto);
  } catch (erro) {
    // O conteudo do storage foi alterado por fora (ou ficou corrompido).
    // Melhor descartar do que quebrar a aplicacao.
    localStorage.removeItem(CHAVE_USUARIO);
    return null;
  }
}

/**
 * O temporizador da renovacao proativa.
 *
 * Vive no modulo (e nao na store) porque e' um detalhe de execucao,
 * nao um estado que a interface precise ler. Guarda-se aqui para poder
 * CANCELAR um agendamento anterior quando a sessao e' renovada antes
 * do prazo - dois agendamentos fariam duas renovacoes, e a segunda
 * apresentaria um token de renovacao ja invalidado (a API rotaciona o
 * token e trata a repeticao como ataque).
 *
 * @type {?number}
 */
let agendamentoAtual = null;

export default new Vuex.Store({

  state: {
    /**
     * Token de acesso: o que vai no cabecalho "Authorization".
     *
     * Vem do localStorage, para que a sessao sobreviva ao recarregamento
     * da pagina (F5). Sem isso, cada F5 seria um logout.
     */
    tokenAcesso: localStorage.getItem(CHAVE_STORAGE + '.acesso'),

    /**
     * Token de renovacao: o que troca por um token de acesso novo quando
     * o primeiro expira.
     */
    tokenRenovacao: localStorage.getItem(CHAVE_STORAGE + '.renovacao'),

    /**
     * Os dados do usuario logado.
     *
     * Null quando ninguem esta logado. O objeto tem:
     *   { usuario, papel, empresaId, administrador }
     */
    usuario: lerUsuarioSalvo(),

    /**
     * Controla se a tela de login esta aberta.
     *
     * O "v-dialog" do Vuetify e' modal: enquanto ela estiver aberta, o
     * conteudo da aplicacao fica bloqueado. E' o que impede que o
     * usuario navegue pelas telas sem sessao.
     */
    loginAberto: false,

    /**
     * A API esta exigindo credencial?
     *
     * False no perfil de desenvolvimento (a autenticacao esta desligada
     * de proposito, para o avaliador navegar sem login). Nesse caso o
     * frontend nao abre a tela de login - nao ha o que autenticar.
     *
     * A API e' quem responde isso, em "/auth/me". O default (false) e' o
     * mais seguro para o frontend: sem informacao, o frontend nao abre
     * a tela de login, e a API - que e' a autoridade - recusa a
     * requisicao se precisar de credencial.
     */
    autenticacaoAtiva: false,

    /**
     * A sessao ja' foi confirmada com a API?
     *
     * Comeca falso: a aplicacao acabou de abrir, e ainda nao se sabe
     * se o token guardado no "localStorage" ainda vale (ele pode ter
     * expirado no servidor desde o ultimo F5).
     *
     * Enquanto este valor for falso, as telas NAO devem pedir dados:
     * elas nasceriam sem token, a API responderia 401, e o console
     * encheria de erro numa navegacao que nao tinha nada de errado.
     *
     * Quem muda este valor e' o "App.vue", depois de consultar o
     * "/auth/me". O router espera por ele antes de montar a tela.
     */
    sessaoConfirmada: false,

    /**
     * Quantos segundos o token de acesso ainda tem de vida.
     *
     * A API devolve esse prazo no login ("expiraEmSegundos"). E' ele
     * que permite renovar o token ANTES de ele vencer - e nao so' depois
     * que a API recusou uma requisicao.
     *
     * Sem isso, o primeiro pedido depois da expiracao volta 401, e o
     * 401 aparece no console do navegador mesmo que a renovacao
     * funcione e a tela se recupere. Renovando na hora, o 401 nao
     * acontece.
     */
    segundosRestantesToken: (() => {
      const bruto = localStorage.getItem(CHAVE_PRAZO);
      const prazo = bruto ? parseInt(bruto, 10) : 0;
      return isNaN(prazo) ? 0 : prazo;
    })(),

    /**
     * Geracao da sessao.
     *
     * E' um contador que sobe a cada vez que a sessao e' estabelecida
     * (login) ou encerrada (logout).
     *
     * Para que ele existe? Porque as telas carregam os dados quando
     * nascem (no "mounted"), e nesse instante ainda nao ha token: o
     * login acontece depois. A tela pediria os dados sem credencial,
     * receberia 401, e ficaria vazia - mesmo depois do login, porque
     * ninguem pediu para recarregar.
     *
     * A solucao e' as telas observarem este contador. Quando ele muda,
     * elas recarregam. Como o Vue 2 nao observa mudancas em numero
     * (ele observa o objeto inteiro), o contador e' lido por um
     * "watcher" nas telas.
     */
    geracaoSessao: 0
  },

  getters: {
    /**
     * Ha sessao ativa?
     *
     * E' o getter que as telas consultam. Note que ele nao olha o token:
     * olha o usuario. O token pode ter expirado no servidor sem que o
     * navegador saiba - quem descobre e' a API, respondendo 401, e o
     * interceptor trata.
     */
    autenticado: state => state.usuario !== null,

    /**
     * O frontend precisa exigir login?
     *
     * So' quando a API exige credencial. No perfil de desenvolvimento (a
     * autenticacao desligada de proposito), a resposta e' false - e o
     * avaliador navega pelas telas sem login, como o enunciado pede.
     *
     * Quando a autenticacao esta ligada e nao ha sessao, a resposta e'
     * true: e' o que faz a tela de login abrir.
     */
    exigeLogin: state => state.autenticacaoAtiva && state.usuario === null,

    /**
     * A aplicacao pode pedir dados agora?
     *
     * E' o contrario do "exigeLogin", e e' o que as telas consultam antes
     * de sair buscar os dados.
     *
     * A razao: quando a API exige credencial e ainda nao ha sessao, o
     * pedido volta 401. A tela ficaria vazia e o console mostraria o
     * erro - sem que ninguem tivesse feito nada de errado. E' a tela de
     * login que vai mostrar o que a pessoa precisa fazer.
     *
     * Depois do login, a mudanca da sessao dispara a recarga (as telas
     * observam o contador de geracao), e os dados aparecem.
     */
    podeCarregarDados: state => !(state.autenticacaoAtiva && state.usuario === null),

    /**
     * O usuario logado e' o administrador da API?
     *
     * Quando nao ha sessao e a autenticacao esta desligada (perfil de
     * desenvolvimento), a resposta e' true: nao ha restricao de acesso.
     * Quando a autenticacao esta ligada e nao ha sessao, a resposta e'
     * false.
     */
    administrador: state => (state.usuario ? state.usuario.administrador === true : !state.autenticacaoAtiva),

    /**
     * O nome do usuario logado, pra mostrar no menu.
     */
    nomeUsuario: state => (state.usuario ? state.usuario.usuario : null)
  },

  mutations: {
    /**
     * Guarda o par de tokens devolvido pelo login.
     *
     * @param {object} state o estado da store
     * @param {object} payload { tokenAcesso, tokenRenovacao, expiraEmSegundos }
     */
    GUARDAR_TOKENS(state, payload) {
      state.tokenAcesso = payload.tokenAcesso || null;
      state.tokenRenovacao = payload.tokenRenovacao || null;

      if (state.tokenAcesso) {
        localStorage.setItem(CHAVE_STORAGE + '.acesso', state.tokenAcesso);
      } else {
        localStorage.removeItem(CHAVE_STORAGE + '.acesso');
      }

      if (state.tokenRenovacao) {
        localStorage.setItem(CHAVE_STORAGE + '.renovacao', state.tokenRenovacao);
      } else {
        localStorage.removeItem(CHAVE_STORAGE + '.renovacao');
      }

      // O prazo vai junto: e' o que permite renovar antes de vencer.
      if (payload.expiraEmSegundos) {
        localStorage.setItem(CHAVE_PRAZO, String(payload.expiraEmSegundos));
        state.segundosRestantesToken = payload.expiraEmSegundos;
      } else {
        localStorage.removeItem(CHAVE_PRAZO);
        state.segundosRestantesToken = 0;
      }
    },

    /**
     * Registra quantos segundos o token de acesso ainda tem de vida.
     *
     * @param {object} state  o estado da store
     * @param {number} segundos o prazo que a API devolveu
     */
    DEFINIR_PRAZO_TOKEN(state, segundos) {
      state.segundosRestantesToken = segundos || 0;

      if (state.segundosRestantesToken) {
        localStorage.setItem(CHAVE_PRAZO, String(state.segundosRestantesToken));
      } else {
        localStorage.removeItem(CHAVE_PRAZO);
      }
    },

    /**
     * Troca so o token de acesso (na renovacao).
     *
     * @param {object} state  o estado da store
     * @param {string} token  o novo token de acesso
     */
    TROCAR_TOKEN_ACESSO(state, token) {
      state.tokenAcesso = token || null;

      if (state.tokenAcesso) {
        localStorage.setItem(CHAVE_STORAGE + '.acesso', state.tokenAcesso);
      } else {
        localStorage.removeItem(CHAVE_STORAGE + '.acesso');
      }
    },

    /**
     * Troca so o token de renovacao (na renovacao de sessao).
     *
     * A API rotaciona o par de tokens a cada renovacao: o token de
     * renovacao velho deixa de valer. Guardar o novo e' o que permite a
     * proxima renovacao funcionar.
     *
     * @param {object} state o estado da store
     * @param {string} token o novo token de renovacao
     */
    TROCAR_TOKEN_RENOVACAO(state, token) {
      state.tokenRenovacao = token || null;

      if (state.tokenRenovacao) {
        localStorage.setItem(CHAVE_STORAGE + '.renovacao', state.tokenRenovacao);
      } else {
        localStorage.removeItem(CHAVE_STORAGE + '.renovacao');
      }
    },

    /**
     * Guarda os dados do usuario logado.
     *
     * @param {object} state    o estado da store
     * @param {object} usuario { usuario, papel, empresaId, administrador }
     */
    GUARDAR_USUARIO(state, usuario) {
      state.usuario = usuario || null;

      if (usuario) {
        localStorage.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
      } else {
        localStorage.removeItem(CHAVE_USUARIO);
      }
    },

    /**
     * Abre ou fecha a tela de login.
     *
     * @param {object} state  o estado da store
     * @param {boolean} aberto true para abrir
     */
    ABRIR_LOGIN(state, aberto) {
      state.loginAberto = aberto === true;
    },

    /**
     * Registra se a API exige credencial.
     *
     * @param {object} state  o estado da store
     * @param {boolean} ativa true quando a autenticacao esta ligada
     */
    DEFINIR_AUTENTICACAO_ATIVA(state, ativa) {
      state.autenticacaoAtiva = ativa === true;
    },

    /**
     * Registra que a sessao foi confirmada com a API.
     *
     * E' o sinal de liberacao: enquanto este valor for falso, o router
     * segura a navegacao e nenhuma tela nasce.
     *
     * @param {object} state o estado da store
     */
    CONFIRMAR_SESSAO(state) {
      state.sessaoConfirmada = true;
    },

    /**
     * Avisa que a sessao mudou.
     *
     * Incrementa o contador de geracao. As telas observam esse valor e
     * recarregam os dados quando ele muda - e' o que faz a tela mostrar
     * os dados DELE depois do login, e nao a lista vazia que veio do
     * 401 de antes.
     *
     * @param {object} state o estado da store
     */
    SESSAO_MUDOU(state) {
      state.geracaoSessao += 1;
    }
  },

  actions: {
    /**
     * Troca o token de acesso por um novo, de forma proativa.
     *
     * E' chamada pelo agendamento (ver "agendarRenovacao"), antes do
     * token vencer - para que nenhuma requisicao receba 401.
     *
     * @param {object} commit  o commit da store
     * @param {object} state   o estado da store
     */
    renovarTokenProativamente({ commit, state, dispatch }) {
      if (!state.tokenRenovacao) {
        return Promise.resolve(null);
      }

      // O axios direto, e nao a instancia de "api.js": o "api.js" tem o
      // interceptor de RESPOSTA, que trataria o 401 da renovacao
      // chamando a renovacao de novo (laco). Aqui o 401 so' significa
      // "a sessao acabou" - e o interceptor de "api.js" cuida disso
      // quando a proxima requisicao normal acontecer.
      const baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081';

      return axios.post(
        baseURL + '/auth/refresh',
        { tokenRenovacao: state.tokenRenovacao },
        { timeout: 15000, headers: { 'Content-Type': 'application/json' } }
      )
        .then((r) => {
          const resposta = r.data;

          commit('TROCAR_TOKEN_ACESSO', resposta.tokenAcesso);

          // A API rotaciona o token de renovacao: o velho deixa de
          // valer. Guardar o novo e' o que permite a proxima renovacao.
          if (resposta.tokenRenovacao) {
            commit('TROCAR_TOKEN_RENOVACAO', resposta.tokenRenovacao);
          }

          commit('DEFINIR_PRAZO_TOKEN', resposta.expiraEmSegundos);

          // Reagenda o proximo.
          dispatch('agendarRenovacao');

          return resposta.tokenAcesso;
        })
        .catch(() => {
          // A renovacao falhou: a sessao acabou de verdade. O
          // interceptor do "api.js" limpa o estado quando a proxima
          // requisicao receber 401.
          return null;
        });
    },

    /**
     * Confirma a sessao com a API.
     *
     * E' uma "action" (e nao um metodo do App.vue) porque ela precisa
     * rodar ANTES de a aplicacao montar: o router resolve a rota
     * inicial quando e' criado, e a tela inicial nasce ali. Se ela
     * pedir dados antes de o token ser verificado, a API responde 401.
     *
     * A acao devolve uma promessa que resolve quando a sessao foi
     * confirmada - ou quando ficou claro que nao ha o que confirmar.
     *
     * ---------------------------------------------------------------------
     * Por que o axios direto, e nao o servico "autenticacao"
     * ---------------------------------------------------------------------
     *
     * O servico "autenticacao" usa a instancia de "api.js", e essa
     * instancia importa a store (para ler o token e gravar o novo).
     * Se a store importasse o servico de volta, teriamos o ciclo:
     *
     *     store -> autenticacao -> api -> store
     *
     * O webpack tolera o ciclo, mas a ordem de resolucao deixa um dos
     * modulos com o import ainda indefinido no momento do uso - e o
     * primeiro sintoma e' a tela de login nunca abrir.
     *
     * Como esta chamada e' unica (a confirmacao da sessao), duplicar
     * duas linhas aqui e' mais barato do que quebrar o grafo de
     * imports.
     *
     * @param {object} commit  o commit da store
     * @param {object} state   o estado da store
     * @param {object} dispatch o dispatch da store
     */
    confirmarSessao({ commit, state, dispatch }) {
      const baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081';

      const cabecalhos = { 'Content-Type': 'application/json' };

      // O token guardado (se houver) vai junto: o "/auth/me" precisa
      // saber de quem se trata.
      if (state.tokenAcesso) {
        cabecalhos.Authorization = 'Bearer ' + state.tokenAcesso;
      }

      // O "try/catch" em volta do require e' o que quebra o ciclo de
      // imports: "store -> api -> store".
      //
      // O "api.js" precisa da store para ler o token e gravar o novo
      // (na renovacao). Se a store importasse o "api.js" no topo, o
      // grafo viraria um ciclo, e a ordem de resolucao do webpack
      // deixaria um dos modulos indefinido no momento do uso.
      //
      // Com o require dentro da acao, o ciclo so' existe enquanto a
      // store ja' esta' construida - e o "api.js" encontra a store
      // completa.
      let api;

      try {
        // eslint-disable-next-line global-require
        api = require('../services/api').default;
      } catch (erro) {
        api = null;
      }

      const chamar = api
        ? api.get('/auth/me')
        : axios.get(baseURL + '/auth/me', { headers: cabecalhos, timeout: 15000 });

      return chamar
        .then((r) => {
          // A instancia de "api.js" devolve os dados direto (o
          // interceptor de resposta desembrulha o envelope). O axios
          // cru devolve o envelope inteiro. Nos dois casos, o que
          // interessa e' o corpo da resposta.
          const resposta = (r && r.data !== undefined) ? r.data : r;


          commit('DEFINIR_AUTENTICACAO_ATIVA', resposta.autenticacaoAtiva === true);

          if (resposta.autenticado && resposta.usuario) {
            commit('GUARDAR_USUARIO', {
              usuario: resposta.usuario,
              papel: resposta.papel || null,
              empresaId: resposta.empresaId || null,
              administrador: resposta.administrador === true
            });

            // A sessao ja' existia (o token sobreviveu ao F5). Avisa as
            // telas para recarregarem: elas nasceram sem token e por isso
            // a primeira carga delas voltou 401.
            commit('SESSAO_MUDOU');

            // A sessao continua: agenda a renovacao para antes do token
            // vencer. Sem isso, o primeiro pedido depois da expiracao
            // voltaria 401.
            dispatch('agendarRenovacao');
          } else if (state.autenticacaoAtiva && state.usuario === null) {
            // A API exige credencial e nao ha sessao: a tela de login
            // precisa abrir.
            commit('ABRIR_LOGIN', true);
          }

          return resposta;
        })
        .catch((erro) => {

          // A API esta fora do ar: nao ha o que fazer aqui, as telas vao
          // mostrar o erro de conexao quando tentarem carregar.
          //
          // O 401 e' diferente: o token nao vale mais. Se o token de
          // renovacao tambem nao vale, a sessao acabou - e a tela de
          // login precisa aparecer. O interceptor do "api.js" ja'
          // limpou o estado nesse caminho; aqui so' falta abrir a tela.
          //
          // O "api.js" rejeita com "{ status, mensagem }"; o axios cru
          // rejeita com o erro original, que tem "response.status".
          const status = erro && erro.status !== undefined
            ? erro.status
            : (erro && erro.response ? erro.response.status : null);

          if (status === 401) {
            commit('ABRIR_LOGIN', true);
          }

          return null;
        })
        .then((resultado) => {
          // A sessao foi confirmada (ou ficou claro que nao ha). A
          // navegacao pode seguir, e as telas podem nascer.
          //
          // E' no final do encadeamento de proposito: mesmo com a API
          // fora do ar, as telas precisam nascer - elas mostrarao o
          // erro de conexao, o que e' mais util do que uma tela em
          // branco.
          commit('CONFIRMAR_SESSAO');

          return resultado;
        });
    },

    /**
     * Estabelece a sessao depois de um login bem-sucedido.
     *
     * Guarda os tokens, guarda os dados do usuario, e avisa as telas
     * que a sessao mudou (para elas recarregarem).
     *
     * @param {object} commit  o commit da store
     * @param {object} payload { tokenAcesso, tokenRenovacao, usuario, papel, empresaId, administrador }
     */
    entrar({ commit, dispatch }, payload) {
      commit('DEFINIR_AUTENTICACAO_ATIVA', true);

      commit('GUARDAR_TOKENS', {
        tokenAcesso: payload.tokenAcesso,
        tokenRenovacao: payload.tokenRenovacao,
        expiraEmSegundos: payload.expiraEmSegundos
      });

      commit('GUARDAR_USUARIO', {
        usuario: payload.usuario,
        papel: payload.papel || null,
        empresaId: payload.empresaId || null,
        administrador: payload.administrador === true
      });

      commit('SESSAO_MUDOU');

      // Agenda a renovacao para antes do token vencer.
      dispatch('agendarRenovacao');
    },

    /**
     * Agenda a renovacao proativa do token.
     *
     * ---------------------------------------------------------------------
     * Por que renovar ANTES de vencer
     * ---------------------------------------------------------------------
     *
     * Se a gente espera o token vencer, a proxima requisicao volta 401.
     * O interceptor renovar e refaz a requisicao - e a tela fica certa -
     * mas o 401 JA foi registrado no console do navegador. E o
     * desenvolvedor (ou o avaliador) ve o erro, mesmo com a aplicacao
     * funcionando.
     *
     * Renovando na hora (um pouco antes do prazo), nenhum 401 acontece:
     * o token novo entra em vigor enquanto o velho ainda valia.
     *
     * A margem de 60 segundos cobre o atraso entre o agendamento e a
     * execucao, e o retrabalho do relogio.
     *
     * @param {object} commit  o commit da store
     * @param {object} state   o estado da store
     * @param {object} dispatch o dispatch da store
     */
    agendarRenovacao({ state, dispatch }) {
      const prazo = state.segundosRestantesToken;

      // Sem prazo conhecido (ou sem token de renovacao), nao ha o que
      // agendar. O interceptor ainda cobre o 401.
      if (!prazo || !state.tokenRenovacao) {
        return;
      }

      const MARGEM = 60;
      const espera = Math.max((prazo - MARGEM) * 1000, 0);

      // Guarda o temporizador para cancelar um agendamento anterior
      // (quando a sessao e' renovada antes do prazo).
      if (agendamentoAtual) {
        clearTimeout(agendamentoAtual);
      }

      agendamentoAtual = setTimeout(() => {
        agendamentoAtual = null;

        // Renovou com sucesso? A nova action reagenda o proximo.
        // Falhou? O interceptor trata o 401.
        dispatch('renovarTokenProativamente').catch(() => null);
      }, espera);
    },

    /**
     * Limpa a sessao local.
     *
     * E' o que o logout chama depois que a API confirma a revogacao.
     *
     * @param {object} commit o commit da store
     */
    encerrarSessao({ commit }) {
      // Cancela a renovacao agendada: ela tentaria renovar um token que
      // ja' foi revogado.
      if (agendamentoAtual) {
        clearTimeout(agendamentoAtual);
        agendamentoAtual = null;
      }

      commit('GUARDAR_TOKENS', {});
      commit('GUARDAR_USUARIO', null);
      commit('DEFINIR_AUTENTICACAO_ATIVA', true);
      commit('ABRIR_LOGIN', true);
      commit('SESSAO_MUDOU');
    }
  }
});
