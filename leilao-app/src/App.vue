<template>
  <!--
     App.vue - tela principal do sistema (exercicio 4).

     Esta e a "casca" da aplicacao: nao tem regra de negocio nem tabela, e
     o esqueleto que fica sempre visivel em volta de cada tela.

     O enunciado pede aqui um MENU para navegar entre unidades, empresas e
     leiloes; ao clicar, a URL vira #/nomedatela e o conteudo interno troca.

     O <v-app-bar> e o <v-navigation-drawer> sao a barra e o menu de
     navegacao. No Vue 2 o template precisa ter UM unico elemento raiz, e o
     <v-app> cumpre esse papel: os dois ficam dentro dele. O <router-view/>
     e o "buraco" onde o vue-router encaixa a tela da rota atual - e ele que
     faz a troca de conteudo acontecer sozinha.

     A lista de links do menu esta em components/AppMenu.vue, como pede o
     enunciado ("separe bem o codigo em componentes").
  -->
  <v-app>
    <!-- ============ barra superior ============ -->
    <!--
      A barra usa a cor de estrutura da AZ (#002436, azul-marinho) - a
      mesma da navbar do site institucional. O logo ao lado e o arquivo
      oficial da empresa (src/assets/brand/logo-az.svg), extraido do site.

      Detalhe sobre o logo:
      O arquivo oficial ja e BRANCO - o site o usa exatamente assim, sobre
      a navbar escura. Aqui a barra tambem e escura, entao o logo aparece
      na cor certa sem nenhum filtro. O arquivo original nao e alterado.
    -->
    <v-app-bar app color="secondary" dark elevation="2">
      <img
        class="logo-az"
        :src="logoAz"
        alt="AZ Tecnologia em Gestao"
      >

      <v-toolbar-title class="titulo-barra">
        Sistema de Leiloes
        <span class="d-none d-sm-inline az-subtitulo">
          &nbsp;|&nbsp; AZ Tecnologia
        </span>
      </v-toolbar-title>

      <v-spacer></v-spacer>

      <!-- Botao que abre/fecha o menu (util em telas pequenas) -->
      <v-app-bar-nav-icon @click="drawer = !drawer"></v-app-bar-nav-icon>
    </v-app-bar>

    <!-- ============ menu lateral ============ -->
    <!--
      "app" faz o menu acompanhar o conteudo na rolagem.
      "v-model" liga o estado "drawer" (aberto/fechado) ao componente.
    -->
    <v-navigation-drawer v-model="drawer" app clipped>
      <app-menu />
    </v-navigation-drawer>

    <!-- ============ conteudo principal ============ -->
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
 * Script do App.vue (a casca da aplicacao).
 *
 * Este componente nao tem regra de negocio: ele monta o menu, a area de
 * notificacoes e o roteador de telas.
 */

/**
 * Importacao dos componentes proprios.
 *
 * Cada arquivo .vue vira um componente Vue. O import abaixo traz o
 * componente do arquivo para dentro deste.
 */
import AppMenu from './components/AppMenu.vue';
import AppNotificacoes from './components/AppNotificacoes.vue';

/**
 * O logo oficial da AZ Tecnologia em Gestao.
 *
 * Por que o import e' necessario, e nao apenas "src=@/assets/...":
 * o webpack so resolve o alias "@" dentro de um atributo `src` de um
 * <img> nativo. No <v-img> do Vuetify, o atributo vira uma propriedade
 * de componente, e o webpack nao a reescreve - o navegador acabaria
 * pedindo "/@/assets/brand/logo-az.svg" e levaria 404. Importando o
 * arquivo, o proprio webpack resolve o caminho e gera a URL correta.
 */
import logoAz from '@/assets/brand/logo-az.svg';

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
   * Dados expostos ao template.
   */
  data() {
    return {
      /**
       * URL do logo (gerada pelo webpack a partir do import acima).
       */
      logoAz,

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
   *  PROVIDE: distributes dados para os componentes filhos.
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
   *  POR QUE EXISTE TAMBEM O "$root.$refs"?
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
 * Estilos globais da aplicacao.
 *
 * A tipografia e as cores vem dos tokens de marca, definidos em
 * src/assets/brand/tokens.css.
 */

/**
 * A fonte da aplicacao inteira. O seletor "html" e o que garante a
 * unificacao: o Vuetify define a fonte em ".v-application", e sem
 * sobrescrever aqui os componentes herdariam a do framework.
 */
html {
  font-family: var(--az-fonte-corpo);
  overflow-y: auto;
}

/**
 * Os titulos usam a fonte de display da marca (Tomorrow), como no site
 * institucional, onde todo h1 ate h5 e' composto nela.
 */
h1,
h2,
h3,
h4,
h5,
.titulo-barra {
  font-family: var(--az-fonte-titulo);
  letter-spacing: 0.01em;
}

/**
 * Cor de fundo da aplicacao. Um neutro muito claro deixa as tabelas
 * (brancas) se destacarem sem brigar com a cor de marca.
 */
.v-application {
  background-color: var(--az-cinza-50) !important;
}

/**
 * O logo na barra superior.
 *
 * O arquivo oficial ja e branco (o site institucional o usa sobre a
 * navbar azul-marinho, igual a esta barra), entao nao e preciso nenhum
 * filtro nem arquivo alternativo.
 */
.logo-az {
  height: 26px;
  width: 112px;
  margin-right: 12px;
}

/**
 * O separador " | AZ Tecnologia" ao lado do titulo.
 *
 * Fica em branco com 80% de opacidade: presenca discreta, sem competir
 * com o nome do sistema.
 */
.az-subtitulo {
  opacity: 0.8;
  font-weight: 400;
  font-family: var(--az-fonte-corpo);
}

/**
 * Estilo do titulo de cada tela (h1 das views).
 *
 * Usa o azul-marinho de estrutura da marca (#002436) em vez do laranja.
 * E uma escolha de acessibilidade: o laranja sobre fundo claro tem
 * contraste de cerca de 3,7:1, que atende o minimo de 3:1 da WCAG AA
 * para texto grande mas nao o de 4,5:1 para texto normal. O azul-marinho
 * passa com folga (16:1).
 */
.titulo-tela {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--az-azul-900);
  margin-bottom: 4px;
}

/**
 * O subtitulo das telas, em texto secundario.
 */
.subtitulo-tela {
  font-size: 0.9rem;
  color: var(--az-cinza-600);
  margin-bottom: 16px;
}
</style>
