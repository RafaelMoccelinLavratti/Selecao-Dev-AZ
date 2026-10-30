/**
 * Arquivo de entrada do frontend.
 *
 * E o primeiro arquivo a rodar quando o navegador abre a aplicacao. Ele cria
 * a instancia do Vue, registra o Vuetify e o router, e monta o componente
 * raiz (App.vue).
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
