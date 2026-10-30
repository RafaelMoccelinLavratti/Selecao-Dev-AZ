<template>
  <!--
     Casca da aplicacao: monta a barra, o menu e o area de notificacoes.
     Nao tem regra de negocio aqui.

     No Vue 2 o template precisa de um unico elemento raiz, e o <v-app>
     cumpre esse papel - a barra e o menu ficam dentro dele. O <router-view/>
     e onde o vue-router encaixa a tela da rota atual.
  -->
  <v-app>
    <!-- barra superior -->
    <!--
      Barra na cor de estrutura da AZ (#002436), a mesma da navbar do site
      institucional. O logo e o arquivo oficial da empresa, que ja vem
      branco (o site usa do mesmo jeito, sobre a navbar escura), entao
      nao precisa de filtro nem de versao alternativa.
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

    <!-- menu lateral -->
    <!--
      "app" faz o menu acompanhar a rolagem do conteudo.
      "v-model" liga o estado "drawer" ao componente.
    -->
    <v-navigation-drawer v-model="drawer" app clipped>
      <app-menu />
    </v-navigation-drawer>

    <!-- conteudo principal -->
    <v-main>
      <v-container fluid class="pa-4">
        <!-- Onde o vue-router encaixa a tela da rota atual. -->
        <router-view />
      </v-container>
    </v-main>

    <!--
      Aviso global de sucesso/erro. Fica na raiz pra qualquer tela
      conseguir chamar.
    -->
    <app-notificacoes ref="notificacoes" />
  </v-app>
</template>

<script>
/**
 * Script do App.vue (a casca da aplicacao).
 * Nao tem regra de negocio: so monta menu, notificacoes e roteador.
 */

/**
 * Cada arquivo .vue vira um componente Vue. O import traz o componente
 * do arquivo pra dentro deste.
 */
import AppMenu from './components/AppMenu.vue';
import AppNotificacoes from './components/AppNotificacoes.vue';

/**
 * Logo oficial da AZ Tecnologia em Gestao.
 *
 * Trago por import (e nao por "src=\"@/assets/...\"") porque ai o webpack
 * resolve o caminho e ainda gera o nome com hash no build.
 */
import logoAz from '@/assets/brand/logo-az.svg';

export default {
  name: 'App',

  // Registro dos componentes. O Vue 2 precisa disso pra "<app-menu />"
  // virar o AppMenu - sem registro ele trataria como tag HTML desconhecida.
  components: {
    AppMenu,
    AppNotificacoes
  },

  data() {
    return {
      // URL do logo, gerada pelo webpack a partir do import acima.
      logoAz,

      // Menu lateral aberto ou fechado. O v-model do <v-navigation-drawer>
      // manda e recebe o valor, entao esta variavel e a fonte da verdade.
      drawer: true
    };
  },

  /**
   * Provide: passa dados pros componentes filhos.
   *
   * O par do "inject". O pai fornece o valor e o filho recebe, sem passar
   * por props atravessando varios componentes no caminho.
   *
   * Aqui fornecemos a funcao "notificar", que e como qualquer tela chama o
   * aviso global:
   *
   *      this.notificar('success', 'Salvo com sucesso.');
   *
   * As views deste projeto tambem chamam por "$root.$refs.notificacoes",
   * que e mais simples (nao precisa declarar inject) e resolve bem num
   * projeto deste tamanho. Num projeto maior eu migraria pro provide/inject.
   */
  provide() {
    return {
      /**
       * Funcao de notificacao disponibilizada pras telas filhas.
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
 * Estilos globais. As cores e as fontes vem de src/assets/brand/tokens.css.
 */

/**
 * A fonte do app inteiro. Precisa sobrescrever no "html" porque o Vuetify
 * define a fonte em ".v-application" - sem isso os componentes herdariam a
 * do framework.
 */
html {
  font-family: var(--az-fonte-corpo);
  overflow-y: auto;
}

/**
 * Titulos na fonte de display da marca (Tomorrow), igual ao site oficial.
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
 * Fundo do app. Um neutro bem claro deixa as tabelas brancas se
 * destacarem sem brigar com a cor de marca.
 */
.v-application {
  background-color: var(--az-cinza-50) !important;
}

/**
 * Logo na barra. O arquivo oficial ja e branco e a barra e escura, igual ao
 * site - por isso nao precisa de filtro nem de versao alternativa.
 */
.logo-az {
  height: 26px;
  width: 112px;
  margin-right: 12px;
}

/**
 * O "| AZ Tecnologia" ao lado do titulo. Branco com 80% de opacidade, pra
 * nao competir com o nome do sistema.
 */
.az-subtitulo {
  opacity: 0.8;
  font-weight: 400;
  font-family: var(--az-fonte-corpo);
}

/**
 * Titulo de cada tela (h1 das views).
 *
 * Usei o azul-marinho de estrutura (#002436) em vez do laranja por causa do
 * contraste: o laranja sobre fundo claro fica em ~3,7:1, que passa no
 * minimo de 3:1 da WCAG AA para texto grande mas nao no de 4,5:1 do texto
 * normal. O azul-marinho passa com folga (16:1).
 */
.titulo-tela {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--az-azul-900);
  margin-bottom: 4px;
}

/**
 * Subtitulo das telas, em texto secundario.
 */
.subtitulo-tela {
  font-size: 0.9rem;
  color: var(--az-cinza-600);
  margin-bottom: 16px;
}
</style>
