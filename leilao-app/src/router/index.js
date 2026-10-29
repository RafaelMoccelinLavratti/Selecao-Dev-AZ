/**
 * ============================================================================
 *  CONFIGURACAO DAS ROTAS (src/router/index.js)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE SAO "ROTAS"?
 *  ------------------------------------------------------
 *  Rotas sao o mapa entre o endereco da URL e a tela que deve aparecer.
 *  O vue-router cuida disso sozinho: quando a URL muda, ele troca o
 *  componente renderizado.
 *
 *  ------------------------------------------------------
 *  POR QUE "mode: 'hash'"?  (exercicio 4)
 *  ------------------------------------------------------
 *  O enunciado pede que a URL fique assim:
 *      http://server/#/unidades
 *      http://server/#/empresas
 *      http://server/#/leiloes
 *
 *  O "#/unidades" e o que chamamos de "hash routing" (SPA com hash).
 *  A vantagem: nao precisa configurar o servidor web para devolver o
 *  index.html a cada URL - o que sempre funciona, ate em hospedagem
 *  simples ou dentro de um container, sem configurar "try_files".
 *
 *  (Se um dia a aplicacao rodar em producao com um dominio proprio, da
 *  para migrar para "mode: 'history'" trocando apenas esta linha - e
 *  ai o servidor web precisaria ser configurado para isso.)
 *
 *  ------------------------------------------------------
 *  O QUE E UM "GUARD"?
 *  ------------------------------------------------------
 *  Um guard (guardiao) e uma funcao que roda ANTES de entrar na tela.
 *  Aqui ele nao bloqueia nada (a aplicacao nao exige login), mas
 *  existe como exemplo do padrao e ja deixa o ponto de entrada
 *  preparado para uma regra de autenticacao no futuro.
 * ============================================================================
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
    // EXERCICIO 5 - tela de consulta e edicao de unidades.
    path: '/unidades',
    name: 'Unidades',
    component: () => import(/* webpackChunkName: "unidades" */ '../views/unidade/Unidades.vue')
  },
  {
    // EXERCICIO 6 - tela de consulta de empresas (somente leitura).
    path: '/empresas',
    name: 'Empresas',
    component: () => import(/* webpackChunkName: "empresas" */ '../views/empresa/Empresas.vue')
  },
  {
    // EXERCICIO 7 - tela de consulta de leiloes (somente leitura).
    // Observacao: o enunciacao do exercicio 4 escreve "leiloes.vue" com
    // a inicial minuscula e o exercicio 7 escreve "Leiloes.vue". Em
    // Windows os dois nomes funcionam; em Linux, so um existe. Adotamos
    // "Leiloes.vue" (comeca com maiuscula, como as outras views).
    path: '/leiloes',
    name: 'Leiloes',
    component: () => import(/* webpackChunkName: "leiloes" */ '../views/leilao/Leiloes.vue')
  },
  {
    // EXERCICIO 8 - tela de INCLUSAO de empresa.
    path: '/empresa',
    name: 'EmpresaIncluir',
    component: () => import(/* webpackChunkName: "empresa" */ '../views/empresa/Empresa.vue')
  },
  {
    // EXERCICIO 8 - tela de EDICAO de empresa.
    // Esta rota PRECISA vir antes da rota "/empresa" para que o "id" seja
    // interpretado como parametro e nao como parte de outro caminho.
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
  // "hash" = a URL fica http://servidor/#/unidades (exatamente como o
  // enunciado pede no exercicio 4).
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
