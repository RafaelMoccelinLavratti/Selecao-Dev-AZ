<template>
  <!--
     Menu de navegacao (components/AppMenu.vue).

     Fica num componente proprio (e nao dentro do App.vue) pra deixar o
     App.vue enxuto, permitir reuso e porque o enunciado pede "separe bem
     o codigo em componentes".

     O menu usa <router-link>, que e o atalho do vue-router: desenha um link
     de verdade (da pra abrir em nova aba e copiar a URL) e, ao clicar,
     troca a tela E a URL.

     Este componente tem um unico elemento raiz (v-list). O Vue 2 exige isso:
     um template precisa ter exatamente um elemento raiz.
  -->
  <v-list
    nav
    dense
    class="menu-navegacao"
    role="navigation"
    aria-label="Navegação principal"
  >
    <!--
      "exact" garante que so o item da pagina atual fique destacado. Sem
      isso, "Empresas" continuaria marcado ao navegar pra "Leiloes".
    -->
    <v-list-item
      v-for="item in itens"
      :key="item.rota"
      :to="item.rota"
      link
      exact
      :aria-current="item.rota === $route.path ? 'page' : null"
    >
      <v-list-item-icon>
        <!--
          O icone e' decorativo: o texto ao lado ja' diz o que e'. O
          "aria-hidden" evita que o leitor de tela leia o nome do icone
          antes do texto ("gavel, Leiloes").
        -->
        <v-icon aria-hidden="true">{{ item.icone }}</v-icon>
      </v-list-item-icon>
      <v-list-item-content>
        <v-list-item-title>{{ item.titulo }}</v-list-item-title>
        <v-list-item-subtitle>{{ item.descricao }}</v-list-item-subtitle>
      </v-list-item-content>
    </v-list-item>

    <!--
      Separador + informacao da conexao. Mostrar com qual ambiente a
      pessoa esta trabalhando evita o erro classico de cadastrar no banco
      errado sem perceber.
    -->
    <v-divider class="my-2"></v-divider>

    <!--
      Quem esta logado e com que papel.

      Mostrar o papel nao e' enfeite: e' a forma de a pessoa entender
      por que uma empresa so' ve os proprios leiloes. E o lembrete de
      que a sessao esta ativa.
    -->
    <div v-if="usuarioLogado" class="pa-3">
      <div class="text-caption text--secondary">
        <v-icon small aria-hidden="true">mdi-account-circle</v-icon>
        {{ nomeUsuario }}
      </div>
      <div class="text-caption text--secondary mt-1">
        {{ descricaoDoPapel }}
      </div>
    </div>

    <v-divider v-if="usuarioLogado" class="my-2"></v-divider>

    <div class="pa-3">
      <div class="text-caption text--secondary">
        <v-icon small aria-hidden="true">mdi-database</v-icon>
        PostgreSQL &middot; Spring Boot &middot; Vue
      </div>
      <div class="text-caption text--secondary mt-1">
        API: {{ enderecoApi }}
      </div>
    </div>

    <!--
      Botao de recolher o menu, no rodape da propria sidebar.

      Fica aqui, e nao na barra superior, porque o menu e' a estrutura de
      navegacao: quem olha para ele procura a saida para as telas, e o
      botao de recolher faz parte dessa navegacao.

      O "aria-expanded" diz ao leitor de tela se o menu esta' aberto ou
      fechado - sem ele, a pessoa so' descobre o estado pela acao.
    -->
    <v-divider class="my-2"></v-divider>

    <div class="pa-2">
      <v-btn
        text
        block
        :aria-expanded="menuAberto ? 'true' : 'false'"
        aria-controls="conteudo-principal"
        @click="$emit('alternar-menu')"
      >
        <v-icon left aria-hidden="true">{{ menuAberto ? 'mdi-chevron-left' : 'mdi-chevron-right' }}</v-icon>
        {{ menuAberto ? 'Recolher menu' : 'Expandir menu' }}
      </v-btn>
    </div>
  </v-list>
</template>

<script>
/**
 * Script do componente de menu.
 *
 * O menu lista as telas a partir da lista de rotas do router. Ele existe
 * separado porque deixa o App.vue enxuto e permite marcar o item ativo.
 */
export default {
  name: 'AppMenu',

  props: {
    /**
     * O menu esta' aberto?
     *
     * Vem do pai (App.vue), que e' quem controla o "v-navigation-drawer".
     * E' passado por prop - e nao lido do "$parent" - porque assim o
     * componente nao depende da posicao na arvore, e o teste pode
     * verificar o "aria-expanded" sem montar a aplicacao inteira.
     */
    menuAberto: {
      type: Boolean,
      default: true
    }
  },

  computed: {
    /**
     * Ha alguem logado?
     */
    usuarioLogado() {
      return this.$store.getters.autenticado;
    },

    /**
     * O nome do usuario logado.
     */
    nomeUsuario() {
      return this.$store.getters.nomeUsuario;
    },

    /**
     * A descricao do papel, em linguagem de quem usa a tela.
     *
     * "Administrador" e "Empresa" dizem mais do que "ROLE_ADMIN" e
     * "ROLE_EMPRESA" - e dizem por que as telas mostram coisas
     * diferentes para cada um.
     */
    descricaoDoPapel() {
      if (!this.usuarioLogado) {
        return '';
      }

      return this.$store.getters.administrador
        ? 'Administrador — acesso total'
        : 'Empresa — somente os próprios dados';
    }
  },

  data() {
    return {
      // Itens do menu. Declarar como dados (e nao escrever cada link no
      // template) e o que deixa a lista crescer sem mexer no HTML.
      itens: [
        {
          // Consulta e edicao de unidades (exercicio 5).
          rota: '/unidades',
          titulo: 'Unidades',
          descricao: 'Cadastro e edição',
          icone: 'mdi-office-building'
        },
        {
          // Consulta de empresas (exercicio 6).
          rota: '/empresas',
          titulo: 'Empresas',
          descricao: 'Consulta de participantes',
          icone: 'mdi-domain'
        },
        {
          // Consulta de leiloes (exercicio 7).
          rota: '/leiloes',
          titulo: 'Leilões',
          descricao: 'Consulta e totais',
          icone: 'mdi-gavel'
        }
      ],

      // Endereco da API, mostrado no rodape do menu. Vem do .env
      // (VUE_APP_API_BASE_URL); o "||" e o plano B caso a variavel nao
      // exista.
      enderecoApi: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081'
    };
  }
};
</script>
