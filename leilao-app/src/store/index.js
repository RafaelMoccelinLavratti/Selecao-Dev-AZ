/**
 * Store global (Vuex).
 *
 * O Vuex e o estado compartilhado da aplicacao. Hoje ele esta vazio porque
 * nenhuma tela precisa compartilhar dados: cada uma carrega os seus da API.
 *
 * Se a API passar a exigir login, o usuario e o token vao para ca; se
 * houver uma busca global, o termo pesquisado tambem.
 */
import Vue from 'vue';
import Vuex from 'vuex';

/**
 * Habilita o Vuex na aplicacao.
 *
 * O "Vue.use(Vuex)" e o que instala o plugin no Vue. Sem essa linha, o
 * this.$store nao existiria em nenhum componente.
 */
Vue.use(Vuex);

/**
 * Cria e exporta a store (o "estado global" da aplicacao).
 *
 * O export default e o que permite o import de outro arquivo:
 *
 *     import store from './store';
 *     new Vue({ store, router, ... })
 *
 * (esse import acontece em src/main.js)
 */
export default new Vuex.Store({

  /**
   *  ESTADO: os dados guardados pela aplicacao.
   *
   *  Hoje vazio, porque nenhuma tela precisa compartilhar dados (veja a
   *  explicacao no cabecalho deste arquivo).
   *
   *  Se um dia existir estado global, ele entra aqui. Exemplo do que
   *  ficaria:
   *
   *      state: {
   *        // Usuario autenticado (null = nao autenticado).
   *        usuario: null,
   *        // Token de acesso (guardado APENAS em memoria - ver a nota
   *        // de seguranca abaixo).
   *        token: null
   *      }
   */
  state: {
  },

  /**
   *  MUTATIONS: as alteracoes do estado.
   *
   *  Sempre synchronous e sempre dentro do "strict mode" do Vuex.
   *
   *  A convencao de nome e "tipoDaAcao/efeito" (ex.: "usuario/definir"),
   *  o que deixa o log de acoes legivel.
   */
  mutations: {
  },

  /**
   *  ACTIONS: as operacoes assincronas.
   *
   *  Uma action pode ser assincrona (fazer uma chamada HTTP, por
   *  exemplo) e, no final, COMMITTA uma mutation. E o padrao oficial do
   *  Vuex para anything que envolva rede.
   */
  actions: {
  },

  /**
   *  MODULES: sub-areas do estado.
   *
   *  Quando o estado cresce, divide-se em modulos (um por dominio:
   *  "usuario", "unidades", "leiloes"), cada um com seus proprios
   *  state/mutations/actions/getters. Isso evita que um unico arquivo
   *  vire um monstro.
   */
  modules: {
  }

  /**
   *  GETTERS: valores derivados do estado.
   *
   *  Nao precisam ser declarados aqui enquanto nao houverem - o Vuex
   *  aceita getters definidos no objeto raiz tambem. A secao existe
   *  para deixar o lugar claro.
   */
});
