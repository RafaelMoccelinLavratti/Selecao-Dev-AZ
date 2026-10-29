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
    <v-app-bar app color="primary" dark elevation="2">
      <v-icon class="mr-2">mdi-gavel</v-icon>
      <v-toolbar-title class="font-weight-bold">
        Sistema de Leiloes
        <span class="d-none d-sm-inline" style="opacity: 0.8; font-weight: 400;">
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
 * Importacao dos componentes proprios.
 */
import AppMenu from './components/AppMenu.vue';
import AppNotificacoes from './components/AppNotificacoes.vue';

export default {
  name: 'App',

  components: {
    AppMenu,
    AppNotificacoes
  },

  data() {
    return {
      // Controla se o menu lateral esta aberto (v-model).
      drawer: true
    };
  },

  /**
   * Exponho o componente de notificacoes para que as telas filhas
   * alcancem o por this.$root.$refs.notificacoes.
   *
   * ALTERNATIVA MAIS LIMPA (e a que eu usaria num projeto real):
   * um "event bus" (Vuex ou um EventEmitter). Para este tamanho de
   * projeto, o $root.$refs resolve bem e nao cria complexidade extra.
   */
  provide() {
    return {
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
 * Estilos GLOBAIS da aplicacao (nao do componente).
 *
 * A fonte padrão é definida aqui para que todo o sistema use a mesma
 * tipografia - consistência visual e metade do trabalho de design.
 */
html {
  font-family: 'Roboto', 'Helvetica Neue', Arial, sans-serif;
  overflow-y: auto;
}

/**
 * Cor de fundo da aplicacao. Um cinza bem claro deixa as tabelas
 * (brancas) se destacarem.
 */
.v-application {
  background-color: #f4f6f8 !important;
}

/**
 * Estilo do titulo de cada tela (h1 das views).
 */
.titulo-tela {
  font-size: 1.5rem;
  font-weight: 500;
  color: #0f4c75;
  margin-bottom: 4px;
}

.subtitulo-tela {
  font-size: 0.9rem;
  color: #6b7280;
  margin-bottom: 16px;
}
</style>
