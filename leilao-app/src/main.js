/**
 * ============================================================================
 *  ARQUIVO DE ENTRADA DO FRONTEND (src/main.js)
 * ============================================================================
 *
 *  Este e o arquivo que roda primeiro quando o navegador abre a aplicacao.
 *  Ele:
 *    1. importa o Vue e registra os plugins (Vuetify, rotas, store);
 *    2. monta o componente raiz (App.vue);
 *    3. "pendura" a aplicacao no elemento HTML com id="app".
 *
 *  O elemento <div id="app"></div> esta no arquivo public/index.html.
 * ============================================================================
 */
import Vue from 'vue';

import App from './App.vue';
import router from './router';
import store from './store';
import vuetify from './plugins/vuetify';

// Mensagens de erro de producao sao silenciadas no Vue (o Vue 2 mostra
// warnings no console). O que exibimos ao usuario e o alerta da tela.
Vue.config.productionTip = false;

new Vue({
  router,
  store,
  vuetify,
  render: h => h(App)
}).$mount('#app');
