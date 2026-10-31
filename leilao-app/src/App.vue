<template>
  <!--
     Casca da aplicacao: monta a barra, o menu e o area de notificacoes.
     Nao tem regra de negocio aqui.

     No Vue 2 o template precisa de um unico elemento raiz, e o <v-app>
     cumpre esse papel - a barra e o menu ficam dentro dele. O <router-view/>
     e onde o vue-router encaixa a tela da rota atual.

     A estrutura visual (sidebar + header) segue o padrao de aplicacoes
     administrativas: a navegacao fica na lateral, e o header carrega
     apenas o "onde eu estou" (a trilha de navegacao).
  -->
  <v-app>
    <!--
      Link para pular direto para o conteudo.

      E' a primeira coisa que a pessoa encontra ao apertar "Tab": um link
      que leva ao conteudo, pulando o menu inteiro. Para quem usa leitor
      de tela, e' o que evita ter que percorrer todos os itens do menu a
      cada troca de tela.

      Ele fica escondido visualmente (fora da tela) e aparece quando
      recebe o foco - o padrao "skip link" da WAI-ARIA.
    -->
    <a
      href="#conteudo-principal"
      class="pular-para-conteudo"
    >
      Pular para o conteúdo principal
    </a>

    <!--
      Barra superior.

      Ela tem duas funcoes: mostrar a trilha de navegacao (o caminho
      que a pessoa percorreu) e, a direita, quem esta logado.

      O logo NAO fica aqui: ele desceu para a sidebar, junto com o
      titulo do sistema. O cabecalho virou uma area de orientacao, e
      nao mais de identificacao.
    -->
    <v-app-bar
      app
      color="secondary"
      dark
      elevation="2"
      :height="64"
    >
      <!--
        Botao que abre o menu.

        Aparece quando o menu esta' recolhido - seja em tela pequena
        (ele vira modal), seja porque a pessoa recolheu de proposito
        (no botao do rodape da sidebar).

        Sem ele, quem recolhesse o menu nao teria como reabri-lo sem
        saber o atalho "Alt+M". E o botao do rodape da sidebar fica
        fora de alcance justamente quando o menu esta' recolhido - e
        um botao que some quando voce precisa dele nao serve.

        O "aria-label" e' obrigatorio: um botao so' com icone nao' tem
        texto, e o leitor de tela precisa saber o que ele faz.
      -->
      <v-btn
        icon
        class="d-md-none"
        aria-label="Abrir menu de navegação"
        @click="drawer = !drawer"
      >
        <v-icon aria-hidden="true">mdi-menu</v-icon>
      </v-btn>

      <!--
        Botao de reabrir o menu, para quem o recolheu de proposito.

        Fica no cabecalho porque o botao do rodape da sidebar fica
        inacessivel enquanto ela esta' recolhida. E' a garantia de que
        nunca existe um estado em que o menu nao da para abrir.

        O "d-none d-md-flex" esconde em tela pequena: la' o botao de
        cima (o que so' aparece no celular) ja' cumpre esse papel, e
        dois botoes com o mesmo icone atrapalhariam.

        O "v-show" (e nao o "v-if") mantem o botao no DOM - assim o
        leitor de tela sempre o encontra, e o foco nao se perde quando
        ele aparece.
      -->
      <v-btn
        v-show="!drawer"
        icon
        class="d-none d-md-flex ml-1"
        aria-label="Reabrir menu de navegação"
        @click="drawer = true"
      >
        <v-icon aria-hidden="true">mdi-menu</v-icon>
      </v-btn>

      <!--
        Trilha de navegacao. Diz onde a pessoa esta, e permite voltar
        um nivel.

        O "v-app-bar-title" aqui nao serve: ele e' para o titulo do
        aplicativo. A trilha e' um landmark de navegacao, e precisa
        da propria semantica.
      -->
      <app-trilha-navegacao class="trilha-no-cabecalho" />

      <v-spacer></v-spacer>

      <!--
        Quem esta logado, e o botao de sair.

        So' aparece quando ha sessao. No perfil de desenvolvimento (a
        autenticacao desligada), o bloco inteiro fica oculto: nao ha
        quem sair.
      -->
      <div v-if="usuarioLogado" class="d-flex align-center mr-2">
        <v-icon small left color="white" aria-hidden="true">mdi-account-circle</v-icon>
        <span class="text-body-2">{{ nomeUsuario }}</span>

        <v-btn
          text
          small
          class="ml-2"
          title="Sair do sistema"
          @click="sair"
        >
          <v-icon left small aria-hidden="true">mdi-logout</v-icon>
          Sair
        </v-btn>
      </div>
    </v-app-bar>

    <!--
      Menu lateral.

      "app" faz o menu acompanhar a rolagem do conteudo.
      "v-model" liga o estado "drawer" ao componente.

      O "id" permite que o botao de recolher aponte para ele
      ("aria-controls"), dizendo ao leitor de tela o que o botao afeta.

      SEM "clipped": com ele, o Vuetify empurra o menu para baixo da
      barra superior ("top: 64px"), e sobrava uma faixa de 64px do
      fundo branco da aplicacao acima do logo - um quadrado branco no
      topo da tela.

      O "clipped" existe para o caso em que a barra ocupa a area toda
      e o menu comeca embaixo dela. Aqui a barra comeca na coluna do
      conteudo (o "left" do app bar ja' e' a largura do menu), entao o
      menu pode e' deve ocupar a coluna inteira, do topo ao rodape.
    -->
    <v-navigation-drawer
      id="menu-lateral"
      v-model="drawer"
      app
      width="280"
    >
      <!--
        Identidade do sistema, no topo da sidebar: logo e nome.

        E' a primeira coisa que a pessoa ve, e o primeiro item da ordem
        de tabulacao. O logo vem como SVG inline (componente
        "LogoAz"), e nao como "<img>": o elemento "<img>" traz o
        "overflow: hidden" do Vuetify, que recorta o desenho, e o
        comportamento de sizing do navegador para SVG com tamanho
        intrinseco - os dois cortavam o "em Gestão" do logo.

        O titulo e' um "div", e nao um cabecalho: o "h1" de cada tela e'
        o titulo da tela (dentro do <router-view>). Assim a pagina nao
        fica com dois "h1".
      -->
      <div class="cabecalho-sidebar">
        <logo-az class="logo-az" />
        <div class="titulo-sistema">
          Sistema de Leilões
        </div>
      </div>

      <v-divider></v-divider>

      <!--
        O menu de navegacao. O botao de recolher fica no rodape dele (veja
        o AppMenu), que e' onde a pessoa procura a saida para as telas.
      -->
      <app-menu
        :menu-aberto="drawer"
        @alternar-menu="drawer = !drawer"
      />
    </v-navigation-drawer>

    <!--
      Conteudo principal.

      O "id" e' o alvo do link "pular para o conteudo" e do
      "aria-controls" do botao de recolher.
    -->
    <v-main id="conteudo-principal">
      <v-container fluid class="pa-4">
        <!--
          A trilha de navegacao, dentro da area de conteudo.

          A barra superior tambem mostra o caminho, mas ela some em tela
          pequena (nao ha espaco para ela e para o bloco do usuario).
          Esta copia fica junto do titulo da tela - que e' onde a pessoa
          olha quando se perde - e aparece em todas as telas, sem que
          cada view precise declarar o componente.

          O "rotulo-navegacao" diferencia esta trilha da barra: para quem
          usa leitor de tela, sao duas areas de navegacao com nomes
          diferentes, e nao dois "navigation" genericos.
        -->
        <app-trilha-navegacao
          class="trilha-na-tela"
          rotulo-navegacao="Caminho até esta tela"
        />

        <!-- Onde o vue-router encaixa a tela da rota atual. -->
        <router-view />
      </v-container>
    </v-main>

    <!--
      Aviso global de sucesso/erro. Fica na raiz pra qualquer tela
      conseguir chamar.
    -->
    <app-notificacoes ref="notificacoes" />

    <!--
      Tela de login. Fica na raiz porque e' ela que bloqueia o
      conteudo quando a API exige credencial e nao ha sessao.
    -->
    <login />
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
import AppTrilhaNavegacao from './components/AppTrilhaNavegacao.vue';
import LogoAz from './components/LogoAz.vue';
import Login from './views/Login.vue';
import { encerrarSessao as encerrarSessaoApi } from './services/autenticacao';

export default {
  name: 'App',

  // Registro dos componentes. O Vue 2 precisa disso pra "<app-menu />"
  // virar o AppMenu - sem registro ele trataria como tag HTML desconhecida.
  components: {
    AppMenu,
    AppNotificacoes,
    AppTrilhaNavegacao,
    LogoAz,
    Login
  },

  computed: {
    /**
     * Ha alguem logado?
     */
    usuarioLogado() {
      return this.$store.getters.autenticado;
    },

    /**
     * O nome do usuario logado, mostrado na barra.
     */
    nomeUsuario() {
      return this.$store.getters.nomeUsuario;
    },

    /**
     * O menu esta' ocupando a tela toda?
     *
     * Abaixo de 960 pixels (o ponto de quebra do Vuetify para o menu
     * virar modal), o menu cobre o conteudo. E' nesse regime que o
     * "Esc" faz sentido: ele fecha uma camada que esta' por cima do
     * conteudo.
     *
     * Em tela grande, o menu esta' ao lado, e fechar com "Esc" seria
     * um comportamento estranho - a pessoa perderia a navegacao sem
     * pedir.
     */
    menuOcupandoTelaPequena() {
      return this.larguraJanela < 960;
    }
  },

  /**
   * "mounted" roda depois que a tela ja' desenhou. E' aqui que se
   * registram os ouvintes globais: o atalho de teclado e o
   * redimensionamento da janela.
   *
   * O "beforeDestroy" desregistra - sem isso, um hot reload do
   * desenvolvimento acumularia ouvintes, e cada tecla dispararia a
   * acao varias vezes.
   */
  mounted() {
    document.addEventListener('keydown', this.aoTeclarTecla);
    window.addEventListener('resize', this.aoRedimensionar);
  },

  beforeDestroy() {
    document.removeEventListener('keydown', this.aoTeclarTecla);
    window.removeEventListener('resize', this.aoRedimensionar);
  },

  data() {
    return {
      // Menu lateral aberto ou fechado. O v-model do <v-navigation-drawer>
      // manda e recebe o valor, entao esta variavel e a fonte da verdade.
      drawer: true,

      /**
       * A largura da janela do navegador.
       *
       * Serve para saber se o menu esta' ocupando a tela toda (telas
       * pequenas) ou apenas ao lado do conteudo (telas grandes). E' o que
       * decide se o "Esc" fecha o menu.
       *
       * O Vuetify ja' faz isso internamente (ele sabe quando o menu
       * vira um "modal"), mas nao expoe. Medir a janela e' uma linha e
       * resolve.
       */
      larguraJanela: window.innerWidth
    };
  },

  methods: {
    /**
     * Guarda a nova largura da janela.
     *
     * O evento "resize" nao e' do Vue: a gente escuta o da janela.
     *
     * @param {Event} evento o evento de redimensionamento
     */
    aoRedimensionar() {
      this.larguraJanela = window.innerWidth;
    },

    /**
     * Atalhos de teclado globais.
     *
     * Dois atalhos, ambos dentro do padrao da WAI-ARIA:
     *
     *   - "Alt" (ou "Ctrl") + "M": alterna o menu lateral. E' o atalho
     *     que os leitores de tela ja' esperam para o menu principal;
     *
     *   - "Esc": fecha o menu, quando ele estiver aberto em tela
     *     pequena. E' o comportamento esperado de um menu que se
     *     sobrepoe ao conteudo.
     *
     * A checagem "o evento veio de um campo de texto" e' essencial:
     * sem ela, digitar "m" numa busca dispararia o atalho, e o
     * usuario nem conseguiria escrever a palavra.
     *
     * @param {KeyboardEvent} evento o evento de teclado
     */
    aoTeclarTecla(evento) {
      // Nao atrapalha quem esta' digitando.
      const alvo = evento.target;
      const digitando = alvo && (
        alvo.tagName === 'INPUT'
        || alvo.tagName === 'TEXTAREA'
        || alvo.isContentEditable
      );

      if (digitando) {
        return;
      }

      // Ctrl+M e' um atalho do navegador (abrir "nova aba"). O "preventDefault"
      // resolve, mas o atalho e' estranho demais - o padrao e' "Alt".
      if (evento.altKey && (evento.key === 'm' || evento.key === 'M')) {
        evento.preventDefault();
        this.drawer = !this.drawer;
        return;
      }

      // Esc fecha o menu. So' quando ele esta' aberto: se a pessoa
      // pressionou Esc numa tela qualquer, nada acontece.
      if (evento.key === 'Escape' && this.drawer && this.menuOcupandoTelaPequena) {
        this.drawer = false;
      }
    },

    /**
     * Sai do sistema.
     *
     * Primeiro pede a revogacao do token a API - e' o que encerra a
     * sessao de verdade (o token de acesso deixa de valer na hora).
     * Depois limpa a sessao local.
     *
     * A ordem importa: se limpassemos primeiro e a API falhasse, o
     * token continuaria valendo no servidor sem ninguem sabendo.
     */
    async sair() {
      const tokenRenovacao = this.$store.state.tokenRenovacao;

      try {
        if (tokenRenovacao) {
          await encerrarSessaoApi(tokenRenovacao);
        }
      } catch (erro) {
        // A API pode estar fora do ar. Mesmo assim, limpa a sessao
        // local: o usuario pediu para sair.
      }

      this.$store.dispatch('encerrarSessao');

      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir('info', 'Sessão encerrada.');
      }
    }
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
 * Foco de teclado visivel, em toda a aplicacao.
 *
 * Este e' um requisito basico de acessibilidade (WCAG 2.1, criterio
 * 2.4.7 - "Focus Visible"): quem navega pelo teclado precisa ver onde
 * esta o foco.
 *
 * O navegador so desenha o contorno automatico em botoes e campos de
 * formulario. Em links, divs clicaveis e itens de menu, ele nao
 * desenha nada - e a pessoa que navega pelo teclado fica sem saber
 * onde esta.
 *
 * O ":focus-visible" (e nao o ":focus") e' o filtro certo: ele so'
 * mostra o contorno quando o foco veio do teclado. Quem navega com o
 * mouse nao ve o contorno aparecendo a cada clique - e assim que o
 * recurso foi projetado.
 *
 * O ":focus:not(:focus-visible)" mantem o contorno para o caso de o
 * navegador nao suporte o filtro - e ai ele sempre aparece, o que e'
 * melhor do que nunca aparecer.
 */
:focus-visible {
  outline: 2px solid var(--az-laranja-500);
  outline-offset: 2px;
}

:focus:not(:focus-visible) {
  outline: none;
}

/**
 * Enlace para pular direto para o conteudo.
 *
 * E' a primeira coisa que a pessoa encontra ao apertar "Tab": um link
 * que leva ao conteudo principal, pulando o menu. Para quem usa
 * leitor de tela, e' o que evita ter que passar por todos os itens do
 * menu a cada troca de tela.
 *
 * O link fica escondido visualmente, mas aparece quando recebe o foco.
 */
.pular-para-conteudo {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 1000;
  background-color: var(--az-azul-900);
  color: var(--az-branco);
  padding: 12px 20px;
  border-radius: 0 0 var(--az-raio-md) 0;
  font-weight: 600;
}

.pular-para-conteudo:focus {
  left: 0;
  outline: 3px solid var(--az-laranja-500);
  outline-offset: -3px;
}

/**
 * O cabecalho da sidebar: logo e nome do sistema.
 *
 * A area do logo e' mais clara que o resto da sidebar (o "primary" da
 * marca), que e' como o site institucional trata a area do logo.
 *
 * O "padding" lateral do cabecalho e' de 16px, igual ao do conteudo do
 * menu. O desenho do logo vai ate a ultima coluna da area util (o "o"
 * final do "Gestao"), entao sem esse respiro ele encostaria na borda da
 * sidebar - e pareceria cortado, mesmo estando completo.
 */
.cabecalho-sidebar {
  background-color: var(--az-azul-800);
  padding: 14px 16px 16px;
}

/**
 * O nome do sistema.
 *
 * O "padding" esquerdo aqui e' o mesmo do conteudo do menu (16px), para
 * o nome ficar alinhado com os itens de navegacao abaixo.
 */
.titulo-sistema {
  color: var(--az-branco);
  font-family: var(--az-fonte-titulo);
  font-size: 1.05rem;
  letter-spacing: 0.01em;
  margin-top: 12px;
  line-height: 1.3;
}

/**
 * A trilha de navegacao no cabecalho.
 *
 * O "min-width: 0" permite que a trilha encolha em vez de empurrar o
 * bloco do usuario para fora da tela.
 */
.trilha-no-cabecalho {
  min-width: 0;
  overflow: hidden;
}

/**
 * A trilha de navegacao dentro da area de conteudo.
 *
 * O componente "AppTrilhaNavegacao" foi desenhado para o cabecalho
 * escuro, entao os textos sao claros. Aqui, sobre fundo claro, as cores
 * precisam ser as do conteudo.
 *
 * O seletor e' de descendente (um espaco), e nao o combinador ">>>".
 *
 * O ">>>" so' funciona em um bloco "<style scoped>": o vue-loader o
 * transforma no atributo de escopo do componente. Fora do "scoped" ele
 * e' CSS invalido, e o navegador descarta a regra INTEIRA sem avisar -
 * o que deixava o item atual da trilha branco sobre fundo branco
 * (sumindo da tela).
 */
.trilha-na-tela .trilha-atual {
  color: var(--az-azul-900);
}

.trilha-na-tela .trilha-link {
  color: var(--az-laranja-600);
}

.trilha-na-tela .trilha-separador {
  opacity: 0.5;
}

/**
 * Um respiro entre a trilha e o titulo da tela.
 */
.trilha-na-tela {
  margin-bottom: 10px;
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
