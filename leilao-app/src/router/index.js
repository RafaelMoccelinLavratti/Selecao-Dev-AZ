/**
 * Configuracao das rotas.
 *
 * O vue-router cuida do mapa entre a URL e a tela: quando a URL muda, ele
 * troca o componente em tela. O endereco usa hash (#/unidades) porque e o
 * que o enunciado pede.
 */
import Vue from 'vue';
import VueRouter from 'vue-router';
import store from '../store';

Vue.use(VueRouter);

const routes = [
  {
    // A raiz abre direto na tela de unidades.
    path: '/',
    redirect: '/unidades'
  },
  {
    // Exercicio 5 - consulta e edicao de unidades.
    path: '/unidades',
    name: 'Unidades',
    component: () => import(/* webpackChunkName: "unidades" */ '../views/unidade/Unidades.vue')
  },
  {
    // Exercicio 6 - consulta de empresas (somente leitura).
    path: '/empresas',
    name: 'Empresas',
    component: () => import(/* webpackChunkName: "empresas" */ '../views/empresa/Empresas.vue')
  },
  {
    // Exercicio 7 - consulta de leiloes (somente leitura).
    //
    // O enunciado do exercicio 4 escreve "leiloes.vue" com a inicial
    // minuscula e o do exercicio 7 escreve "Leiloes.vue". No Windows os
    // dois funcionam; no Linux so um existe. Adotamos "Leiloes.vue", com
    // maiuscula, como as outras views.
    path: '/leiloes',
    name: 'Leiloes',
    component: () => import(/* webpackChunkName: "leiloes" */ '../views/leilao/Leiloes.vue')
  },
  {
    // Exercicio 8 - inclusao de empresa.
    path: '/empresa',
    name: 'EmpresaIncluir',
    component: () => import(/* webpackChunkName: "empresa" */ '../views/empresa/Empresa.vue')
  },
  {
    // Exercicio 8 - edicao de empresa.
    //
    // Esta rota precisa vir antes da rota "/empresa", senao o "id" e
    // interpretado como parte de outro caminho em vez de parametro.
    // O alias "/leilao/:id" cobre o texto do enunciado do exercicio 8.
    path: '/empresa/:id',
    alias: '/leilao/:id',
    name: 'EmpresaEditar',
    component: () => import(/* webpackChunkName: "empresa" */ '../views/empresa/Empresa.vue'),
    props: true
  },
  {
    // Qualquer URL desconhecida volta para a tela inicial.
    path: '*',
    redirect: '/unidades'
  }
];

/**
 * A promessa da confirmacao da sessao.
 *
 * Ela e' criada aqui, no momento em que o modulo e' carregado - e nao
 * depois. Isso e' essencial: o vue-router resolve a rota inicial no
 * momento em que e' criado, e o guardiao global roda nessa navegacao.
 * Se a promessa so' existisse depois, o guardiao nao teria o que esperar
 * e liberaria a tela antes da sessao ser confirmada - que e' exatamente
 * o 401 que aparecia no console.
 *
 * Quem resolve e' o "main.js", que dispara a confirmacao na store.
 */
let resolverConfirmacao = null;

const confirmacaoDaSessao = new Promise((resolver) => {
  resolverConfirmacao = resolver;
});

/**
 * Guarda a promessa da confirmacao.
 *
 * @param {Promise} promessa a promessa que resolve quando a sessao
 *                       foi confirmada
 */
export function registrarConfirmacao(promessa) {
  // A promessa do "main.js" e' a que interessa: quando ela resolver, a
  // sessao foi confirmada.
  //
  // A promessa interna deste modulo so' existe para a barreira do
  // guardiao global ter o que esperar. Ela e' resolvida junto com a
  // externa.
  promessa.then(
    (resultado) => {
      resolverConfirmacao(resultado);
    },
    () => {
      // A confirmacao falhou (a API esta fora do ar). A barreira
      // libera a navegacao mesmo assim: a tela mostrara o erro de
      // conexao, o que e' mais util do que uma tela em branco.
      resolverConfirmacao(null);
    }
  );

  return promessa;
}

const router = new VueRouter({
  // "hash" = a URL fica http://servidor/#/unidades, como o enunciado
  // pede no exercicio 4.
  mode: 'hash',
  routes
});

/**
 * Guardiao global: roda antes de cada troca de pagina.
 *
 * A regra e' simples: se a API esta com a autenticacao ligada e nao ha
 * sessao no navegador, a tela de login abre.
 *
 * Um detalhe importante: a guarda NAO e' a barreira de seguranca. Ela
 * e' experiencia de usuario - evita que a pessoa veja uma tela vazia e
 * um "Erro ao carregar" antes de descobrir que precisa entrar. A
 * barreira de verdade esta no backend, que recusa a requisicao sem
 * credencial (401), e o interceptor do axios, que limpa a sessao
 * expirada e reabre a tela de login.
 *
 * Por isso o "abrir o login" acontece em dois lugares: aqui (antes de
 * a tela carregar) e no interceptor (quando a sessao expira no meio
 * do uso).
 *
 * ---------------------------------------------------------------------
 * A BARREIRA: esperar a sessao antes de montar a tela
 * ---------------------------------------------------------------------
 *
 * O "created" do App.vue e' assincrono (ele consulta o "/auth/me" para
 * descobrir se a sessao sobreviveu ao F5). Mas o Vue NAO aguarda hooks
 * assincronos: o "router-view" monta a tela na mesma hora, e a tela
 * pede os dados antes de o token saber se ainda vale.
 *
 * O resultado era um 401 garantido no console em toda navegacao com a
 * autenticacao ligada: a tela nascia, pedia os dados, a API recusava, e
 * so' depois o interceptor renovava o token e repetia a requisicao.
 *
 * A correcao e' esta barreira: enquanto a sessao nao foi confirmada, a
 * navegacao fica esperando. A tela so' nasce quando o token ja' foi
 * verificado - ou quando o interceptor ja' descobriu que a sessao
 * acabou.
 */
router.beforeEach((to, from, next) => {
  // A tela de login so' e' exigida quando a API exige credencial.
  // No perfil de desenvolvimento (autenticacao desligada), o avaliador
  // navega sem login - como o enunciado pede.
  if (store.getters.exigeLogin) {
    store.commit('ABRIR_LOGIN', true);
  }

  // A sessao ainda esta' sendo confirmada? Espera.
  //
  // Sem esta espera, a tela nasce antes do "/auth/me" responder, e
  // pede os dados sem token - o que produz um 401 em toda navegacao
  // (visivel no console, mesmo com a tela funcionando depois).
  if (!store.state.sessaoConfirmada) {
    // A promessa existe desde o carregamento do modulo (veja a
    // declaracao acima). Aqui ela so' espera ser resolvida.
    confirmacaoDaSessao.then(() => {
      // A sessao foi confirmada. Reavalia a exigencia de login: e' o
      // que decide se a tela de login abre.
      if (store.getters.exigeLogin) {
        store.commit('ABRIR_LOGIN', true);
      }

      next();
    }).catch(() => {
      // A confirmacao falhou (a API esta fora do ar). Deixa a tela
      // carregar mesmo assim: ela mostrara o erro de conexao, que e'
      // mais util do que uma tela em branco.
      next();
    });

    return;
  }

  next();
});

/**
 * Depois de cada navegacao concluida.
 *
 * E' aqui que o router avisa a store que a tela atual mudou. As telas
 * que precisam recarregar (por causa de um login, por exemplo) observam
 * o contador de geracao da sessao.
 *
 * Este gancho tambem cobre o primeiro carregamento: e' ele que garante
 * que a tela inicial so' monte depois que a sessao foi confirmada.
 */
router.afterEach((to, from) => {
  // Nao dispara na primeira resolucao da rota (o "from" e' vazio): a
  // barreira do "beforeEach" ja' cuidou desse caso.
  if (from.name) {
    store.commit('SESSAO_MUDOU');
  }
});

export default router;
