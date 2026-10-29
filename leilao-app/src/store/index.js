/**
 * ============================================================================
 *  STORE GLOBAL (src/store/index.js)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE E O VUEX?
 *  ------------------------------------------------------
 *  O Vuex e a biblioteca oficial de gerenciamento de ESTADO do Vue. Ele
 *  existe para resolver um problema classico do Vue:
 *
 *  O estado (os dados) de um componente vive dentro dele. Se a tela A tem
 *  um dado que a tela B tambem precisa, o padrao "copiar e colar" funciona
 *  em telas de exemplo, mas comeca a dar trabalho em telas de verdade:
 *
 *      - a tela A altera o dado;
 *      - a tela B continua mostrando a versao ANTIGA;
 *      - alguem precisa avisar a tela B de que o dado mudou.
 *
 *  O Vuex centraliza o estado em UM lugar. Qualquer componente le e
 *  escreve no mesmo lugar, e o Vuex avisa automaticamente todos os
 *  interessados quando o valor muda.
 *
 *  ------------------------------------------------------
 *  A ARQUITETURA "UNIDIRECIONAL" (o coracao do Vuex)
 *  ------------------------------------------------------
 *  O fluxo de dados no Vuex tem SEMPRE a mesma direcao:
 *
 *      TELA ---> AÇÃO ---> MUTATION ---> ESTADO ---> TELA
 *                          |
 *                          +--> (o estado mudou, a tela se atualiza)
 *
 *    AÇÃO     : a intencao do usuario ("salvar", "excluir").
 *               E a "porta de entrada" - todas as mudancas passam por
 *               aqui, e por isso e facil registrar logs e depurar.
 *
 *    MUTATION : a mudanca real do estado. E onde o estado e alterado
 *               de fato. Pode ser sincrona e e sempre dentro do
 *               "strict mode" (o Vuex avisa se voce alterar o estado
 *               fora de uma mutation - e isso e proposital, porque
 *               alteracoes fora de ordem causam bugs dificeis de achar).
 *
 *    ESTADO   : o dado em si (um objeto "state").
 *
 *    GETTER  : um valor DERIVADO do estado. Por exemplo, "quantas
 *               unidades estao ativas". Nao se guarda no estado (guardar
 *               seria duplicar dados que podem sair de sincronia) - calcula
 *               na hora, sempre certo.
 *
 *  ------------------------------------------------------
 *  POR QUE ESSE ARQUIVO ESTA VAZIO?
 *  ------------------------------------------------------
 *  Porque, no estado atual do projeto, NAO HA UM ESTADO GLOBAL DE FATO.
 *
 *  Cada tela carrega os seus proprios dados da API e trabalha com eles
 *  localmente:
 *
 *      Unidades.vue  -->  busca /unidades   e guarda em this.unidades
 *      Empresas.vue  -->  busca /empresas   e guarda em this.empresas
 *      Leiloes.vue   -->  busca /leiloes    e guarda em this.leiloes
 *
 *  Nenhuma dessas telas precisa do dado de outra. Nao existe um "usuario
 *  logado" global (a API e aberta por padrao), nem um carrinho, nem uma
 *  preferencia global.
 *
 *  Manter a store vazia e uma DECISAO, e nao uma esquidencia. Um store
 *  so deve existir quando existe estado compartilhado - criar um store
 *  "caso someday" e o primeiro passo para uma aplicacao com estado
 *  inconsistente e bugs impossiveis de reproduzir.
 *
 *  ------------------------------------------------------
 *  O QUE MUDARIA SE SURGISSE UM ESTADO GLOBAL?
 *  ------------------------------------------------------
 *  Dois cenarios reais em que este arquivo ganharia conteudo:
 *
 *  1) AUTENTICACAO NO FRONTEND
 *     Se a API passasse a exigir login, o usuario (nome, perfil, token)
 *     seria o mesmo para todas as telas. Ele iria para o store, e o menu
 *     leria dele para exibir "Ola, Joao" e esconder o que ele nao pode
 *     acessar.
 *
 *  2) FILTROS GLOBAIS
 *     Se houvesse uma busca que afetasse todas as telas (por exemplo, um
 *     "buscar por lote" no topo da barra), o termo pesquisado e as
 *     resultados seriam globais.
 *
 *  A estrutura abaixo ja esta preparada para os dois casos: basta
 *  descomentar e preencher.
 * ============================================================================
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
   * ------------------------------------------------------------------
   *  ESTADO: os dados guardados pela aplicacao.
   * ------------------------------------------------------------------
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
   * ------------------------------------------------------------------
   *  MUTATIONS: as alteracoes do estado.
   * ------------------------------------------------------------------
   *
   *  Sempre synchronous e sempre dentro do "strict mode" do Vuex.
   *
   *  A convencao de nome e "tipoDaAcao/efeito" (ex.: "usuario/definir"),
   *  o que deixa o log de acoes legivel.
   */
  mutations: {
  },

  /**
   * ------------------------------------------------------------------
   *  ACTIONS: as operacoes assincronas.
   * ------------------------------------------------------------------
   *
   *  Uma action pode ser assincrona (fazer uma chamada HTTP, por
   *  exemplo) e, no final, COMMITTA uma mutation. E o padrao oficial do
   *  Vuex para anything que envolva rede.
   */
  actions: {
  },

  /**
   * ------------------------------------------------------------------
   *  MODULES: sub-areas do estado.
   * ------------------------------------------------------------------
   *
   *  Quando o estado cresce, divide-se em modulos (um por dominio:
   *  "usuario", "unidades", "leiloes"), cada um com seus proprios
   *  state/mutations/actions/getters. Isso evita que um unico arquivo
   *  vire um monstro.
   */
  modules: {
  }

  /**
   * ------------------------------------------------------------------
   *  GETTERS: valores derivados do estado.
   * ------------------------------------------------------------------
   *
   *  Nao precisam ser declarados aqui enquanto nao houverem - o Vuex
   *  aceita getters definidos no objeto raiz tambem. A secao existe
   *  para deixar o lugar claro.
   */
});
