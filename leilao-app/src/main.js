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
import { registrarConfirmacao } from './router';

// Mensagens de erro de producao sao silenciadas no Vue (o Vue 2 mostra
// warnings no console). O que exibimos ao usuario e o alerta da tela.
Vue.config.productionTip = false;

/**
 * Confirma a sessao ANTES de montar a aplicacao.
 *
 * Por que antes: o router resolve a rota inicial quando e' criado -
 * ou seja, antes de o App.vue existir. A tela inicial ("/unidades")
 * nasceria ali mesmo, e ela pede os dados no "mounted". Se o token
 * guardado no navegador ja' tiver vencido (expirou no servidor desde o
 * ultimo F5), a API responderia 401 - e o console encheria de erro numa
 * navegacao que nao tinha nada de errado.
 *
 * Confirmar a sessao primeiro deixa o token valido (ou descobre que a
 * sessao acabou) antes de qualquer tela pedir dados.
 *
 * A promessa e' registrada no router: enquanto ela nao resolve, o
 * guardiao global segura qualquer navegacao.
 *
 * O "catch" existe para o caso de a API estar fora do ar: a aplicacao
 * precisa montar de qualquer jeito, para poder mostrar o erro de
 * conexao (uma tela em branco seria pior).
 */
registrarConfirmacao(store.dispatch('confirmarSessao').catch(() => null));

new Vue({
  router,
  store,
  vuetify,
  render: h => h(App)
}).$mount('#app');
