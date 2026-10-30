/**
 * Configuracao das rotas.
 *
 * O vue-router cuida do mapa entre a URL e a tela: quando a URL muda, ele
 * troca o componente em tela. O endereco usa hash (#/unidades) porque e o
 * que o enunciado pede.
 */
import Vue from 'vue';
import VueRouter from 'vue-router';

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
    path: '/empresa/:id',
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

const router = new VueRouter({
  // "hash" = a URL fica http://servidor/#/unidades, como o enunciado
  // pede no exercicio 4.
  mode: 'hash',
  routes
});

/**
 * Guardiao global: roda antes de cada troca de pagina.
 * Comente a linha de dentro para ver o comportamento sem ele.
 */
router.beforeEach((to, from, next) => {
  // Aqui entraria a verificacao de login do usuario, por exemplo:
  //   if (to.meta.exigeLogin && !estaAutenticado()) { next('/login'); return; }
  next();
});

export default router;
