/**
 * Store global (Vuex).
 *
 * Hoje vazia: cada tela carrega o que precisa direto da API, entao ainda
 * nao ha estado compartilhado. Se aparecer login ou busca global, e aqui
 * que o usuario e o token entram.
 */
import Vue from 'vue';
import Vuex from 'vuex';

// Instala o plugin no Vue. Sem essa linha o this.$store nao existiria
// em nenhum componente.
Vue.use(Vuex);

// A store e exportada pra ser importada em src/main.js:
//
//     import store from './store';
//     new Vue({ store, router, ... })
export default new Vuex.Store({

  // Estado compartilhado. Vazio por enquanto (veja o cabecalho).
  state: {
  },

  // Alteracoes do estado. Sempre sincronas.
  mutations: {
  },

  // Operacoes assincronas (chamada HTTP, por exemplo). No fim, commitam
  // uma mutation.
  actions: {
  },

  // Sub-areas do estado, uma por dominio, quando o estado crescer.
  modules: {
  }
});
