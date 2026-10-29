<template>
  <!--
    ==========================================================================
     APP.VUE  |  TELA PRINCIPAL DO SISTEMA   (exercicio 4)
    ==========================================================================
     Esta e a "casca" da aplicacao. Ela NAO tem regra de negocio nem
     tabela: e o esqueleto que fica sempre visivel em volta de cada tela.

     O que o enunciado pede aqui:
       - um MENU para navegar de uma pagina para outra;
       - as opcoes: unidades, empresas e leiloes;
       - ao clicar, a URL vira #/nomedatela e o conteudo interno troca.

     Como isso funciona:
       - <v-app-bar> e <v-navigation-drawer> = a barra e o menu de
         navegacao. No Vue 2 o template precisa ter UM unico elemento raiz,
         e o <v-app> cumpre esse papel: os dois ficam dentro dele.
       - <router-view/> = o "buraco" onde o vue-router encaixa a tela da
         rota atual. E ele que faz a troca de conteudo acontecer sozinha.

     NOTA SOBRE O COMPONENTE AppMenu:
     O menu (a lista de links) esta em components/AppMenu.vue, como pede o
     enunciado ("separe bem o codigo em componentes"). A barra superior
     (v-app-bar) e o container (v-navigation-drawer) ficam aqui, porque o
     Vue 2 exige um unico elemento raiz por componente.
    ==========================================================================
  -->
  <v-app>
    <!-- ============ BARRA SUPERIOR ============ -->
    <!--
      A barra usa a cor de marca da AZ (#14b3cc) no papel "primary" do
      Vuetify. O logo ao lado e o arquivo oficial da empresa
      (src/assets/brand/logo-az.svg), extraido do site institucional.

      DETALHE IMPORTANTE SOBRE A COR DO LOGO:
      O logo oficial e ciano (#14b3cc) - a MESMA cor da barra. Um logo
      ciano sobre uma barra ciano ficaria invisivel. Por isso aplicamos
      um filtro CSS que converte o logo em branco ("brightness(0)" deixa
      tudo preto, "invert(1)" inverte preto para branco). O arquivo
      original NAO e alterado: e o navegador que faz a conversao, e o
      logo continua com a cor oficial em qualquer outro uso.
    -->
    <v-app-bar app color="primary" dark elevation="2">
      <v-img
        class="logo-az"
        src="@/assets/brand/logo-az.svg"
        alt="AZ Tecnologia em Gestao"
        contain
      ></v-img>

      <v-toolbar-title class="font-weight-bold">
        Sistema de Leiloes
        <span class="d-none d-sm-inline az-subtitulo">
          &nbsp;|&nbsp; AZ Tecnologia
        </span>
      </v-toolbar-title>

      <v-spacer></v-spacer>

      <!-- Botao que abre/fecha o menu (util em telas pequenas) -->
      <v-app-bar-nav-icon @click="drawer = !drawer"></v-app-bar-nav-icon>
    </v-app-bar>

    <!-- ============ MENU LATERAL ============ -->
    <!--
      "app" faz o menu acompanhar o conteudo na rolagem.
      "v-model" liga o estado "drawer" (aberto/fechado) ao componente.
    -->
    <v-navigation-drawer v-model="drawer" app clipped>
      <app-menu />
    </v-navigation-drawer>

    <!-- ============ CONTEUDO PRINCIPAL ============ -->
    <v-main>
      <v-container fluid class="pa-4">
        <!--
          Onde a tela da rota atual e renderizada.
          Sem isso, clicar no menu mudaria a URL mas a tela nao mudaria.
        -->
        <router-view />
      </v-container>
    </v-main>

    <!--
      AVISO GLOBAL: e o componente que mostra as mensagens de sucesso/erro.
      Ele vive aqui (raiz) para que qualquer tela possa aciona-lo.
    -->
    <app-notificacoes ref="notificacoes" />
  </v-app>
</template>

<script>
/**
 * ============================================================================
 *  SCRIPT DO APP (a casca da aplicacao)
 * ============================================================================
 *
 *  Este componente nao tem regra de negocio. Ele e responsavel apenas
 *  pela ESTRUTURA: barra superior, menu lateral, area de conteudo e o
 *  aviso global de mensagens.
 *
 *  ------------------------------------------------------
 *  O CICLO DE VIDA DE UM COMPONENTE VUE
 *  ------------------------------------------------------
 *  O Vue tem ganchos (hooks) que sao chamados em momentos especificos da
 *  vida de um componente. Os principais:
 *
 *    created()  : chamado assim que o componente e criado (antes de
 *                 aparecer na tela). Boa hora para preparar dados.
 *    mounted()  : chamado DEPOIS que o componente ja entrou na tela.
 *                 Boa hora para chamar a API (a tela ja existe).
 *    updated()  : chamado depois de uma mudanca reativa.
 *    beforeDestroy() : chamado antes do componente ser removido. Boa
 *                 hora para limpar timers e listeners.
 *
 *  O App.vue nao precisa de nenhum deles: ele nao carrega dados.
 *  Quem precisa e cada tela - por isso cada view chama "this.carregar()"
 *  dentro do "mounted()".
 * ============================================================================
 */

/**
 * Importacao dos componentes proprios.
 *
 * Cada arquivo .vue vira um componente Vue. O import abaixo traz o
 * componente do arquivo para dentro deste.
 */
import AppMenu from './components/AppMenu.vue';
import AppNotificacoes from './components/AppNotificacoes.vue';

export default {
  /**
   * Nome do componente raiz.
   */
  name: 'App',

  /**
   * Registra os componentes usados neste template.
   *
   * O Vue 2 exige esse registro: e ele que faz o HTML entender que
   * "<app-menu />" significa "use o componente AppMenu". Sem o
   * registro, o Vue trataria como uma tag HTML desconhecida (e a tela
   * ficaria em branco).
   *
   * O nome usado no template e o do arquivo em "kebab-case":
   * AppMenu -> <app-menu />.
   */
  components: {
    AppMenu,
    AppNotificacoes
  },

  /**
   * ------------------------------------------------------------------
   *  DADOS REATIVOS (data)
   * ------------------------------------------------------------------
   */
  data() {
    return {
      /**
       * Controla se o menu lateral esta aberto ou fechado.
       *
       * Esta variavel esta ligada ao menu pelo "v-model":
       *
       *      <v-navigation-drawer v-model="drawer" ...>
       *
       * Com o v-model, qualquer mudanca no componente (o usuario
       * arrastando o menu) atualiza a variavel, e vice-versa. E o
       * "v-model" do <v-navigation-drawer> que traz o controle de
       * abrir/fechar, entao a variavel e a "fonte da verdade".
       */
      drawer: true
    };
  },

  /**
   * ------------------------------------------------------------------
   *  PROVIDE: distributes dados para os componentes filhos.
   * ------------------------------------------------------------------
   *
   * O "provide" e o par do "inject". O pai "fornece" um valor e o filho
   * "injeta" (recebe) ele - sem precisar passar por props atravessando
   * varios componentes no meio do caminho.
   *
   * Aqui fornecemos a funcao "notificar", que e a forma oficial (e mais
   * limpa) de qualquer tela chamar o aviso global:
   *
   *      // em qualquer view:
   *      this.notificar('success', 'Salvo com sucesso.');
   *
   * ------------------------------------------------------
   *  POR QUE EXISTE TAMBEM O "$root.$refs"?
   * ------------------------------------------------------
   *  Porque este projeto usa as duas formas, de proposito:
   *
   *  - O "provide/inject" e a forma NOVA, mais limpa e a que eu usaria
   *    num projeto real. Mas ela exige que a view declare o "inject" -
   *    e as views deste projeto foram escritas com a forma simples.
   *
   *  - O "$root.$refs.notificacoes" e a forma SIMPLES: nao precisa
   *    declarar nada na view, funciona direto, e resolve bem num
   *    projeto deste tamanho.
   *
   *  Em um projeto com mais telas, eu migraria para o provide/inject.
   *  Manter as duas agora e uma escolha de transicao consciente, e
   *  esta documentada aqui justamente para nao parecer duplicidade
   *  esquecida.
   *
   *  ALTERNATIVA MAIS ESCALAVEL: um "event bus" (Vuex ou um EventEmitter
   *  proprio). Para este tamanho de projeto, as duas formas acima
   *  resolvem bem e nao criam complexidade extra.
   */
  provide() {
    return {
      /**
       * Funcao de notificacao disponibilizada para as telas filhas.
       *
       * @param {string} tipo     'success', 'error' ou 'info'
       * @param {string} mensagem texto a exibir
       */
      notificar: (tipo, mensagem) => {
        if (this.$refs.notificacoes) {
          this.$refs.notificacoes.exibir(tipo, mensagem);
        }
      }
    };
  }
};
</script>

<style>
/**
 * ============================================================================
 *  ESTILOS GLOBAIS DA APLICACAO
 * ============================================================================
 *
 *  A tipografia e as cores vem dos tokens de marca
 *  (src/assets/brand/tokens.css), extraidos do tema institucional da
 *  AZ Tecnologia em Gestao. Aqui so aplicamos esses tokens.
 *
 *  A fonte institucional e a **Poppins**, carregada no public/index.html.
 *  Antes, a aplicacao usava Roboto - que e a fonte padrao do Material
 *  Design, nao a da empresa. A troca e o que faz a interface parecer
 *  com a marca de verdade.
 */

/**
 * A fonte da aplicacao inteira. O seletor "html" e o que garante a
 * unificacao: o Vuetify define a fonte em ".v-application", e sem
 * sobrescrever aqui os componentes herdariam a do framework.
 */
html {
  font-family: var(--az-font-family);
  overflow-y: auto;
}

/**
 * Cor de fundo da aplicacao. Um neutro muito claro deixa as tabelas
 * (brancas) se destacarem sem brigar com a cor de marca.
 */
.v-application {
  background-color: var(--az-neutral-50) !important;
}

/**
 * O logo na barra superior.
 *
 * O arquivo oficial e ciano (#14b3cc), a mesma cor da barra. Este filtro
 * ("brightness(0) invert(1)") converte qualquer cor em branco, sem
 * alterar o arquivo original - o logo continua com a cor oficial em
 * qualquer outro lugar onde for usado.
 */
.logo-az {
  filter: brightness(0) invert(1);
  height: 30px;
  width: 94px;
  margin-right: 12px;
}

/**
 * O separador " | AZ Tecnologia" ao lado do titulo.
 *
 * Fica em branco com 80% de opacidade: presença discreta, sem competir
 * com o nome do sistema.
 */
.az-subtitulo {
  opacity: 0.8;
  font-weight: 400;
}

/**
 * Estilo do titulo de cada tela (h1 das views).
 *
 * Usa o navy de estrutura da marca (#153c5c) em vez do ciano. E uma
 * escolha de acessibilidade: o ciano sobre fundo claro tem contraste
 * de cerca de 2.4:1, abaixo do minimo de 4.5:1 exigido pela WCAG AA
 * para texto normal. O navy passa com folga.
 */
.titulo-tela {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--az-navy-700);
  margin-bottom: 4px;
}

/**
 * O subtitulo das telas, em texto secundario.
 */
.subtitulo-tela {
  font-size: 0.9rem;
  color: var(--az-neutral-700);
  margin-bottom: 16px;
}
</style>
