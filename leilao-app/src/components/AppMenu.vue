<template>
  <!--
    ==========================================================================
     COMPONENTE: MENU DE NAVEGACAO  (components/AppMenu.vue)
    ==========================================================================
     Fica em um componente proprio (e nao dentro do App.vue) porque:
       - o App.vue fica enxuto e legivel;
       - o menu pode ser reutilizado em outras telas;
       - o enunciado pede "Separe bem o codigo em componentes".

     O menu usa <router-link>, que e o "atalho" do vue-router: ele
     desenha um link de verdade (pode abrir em nova aba, copiar URL) e
     ao clicar, troca a tela E a URL para #/unidades, #/empresas, #/leiloes.

     Este componente tem UM unico elemento raiz (v-list). O Vue 2 exige
     isso: um template precisa ter exatamente um elemento raiz.
    ==========================================================================
  -->
  <v-list nav dense>
    <!--
      NOTA SOBRE "exact": garante que so o item da pagina ATUAL fique
      destacado. Sem isso, "Empresas" continuaria marcado ao navegar
      para "Leiloes".
    -->
    <v-list-item
      v-for="item in itens"
      :key="item.rota"
      :to="item.rota"
      link
      exact
    >
      <v-list-item-icon>
        <v-icon>{{ item.icone }}</v-icon>
      </v-list-item-icon>
      <v-list-item-content>
        <v-list-item-title>{{ item.titulo }}</v-list-item-title>
        <v-list-item-subtitle>{{ item.descricao }}</v-list-item-subtitle>
      </v-list-item-content>
    </v-list-item>

    <!--
      SEPARADOR + INFORMACAO DA CONEXAO.
      Um sistema interno mostra com qual ambiente o usuario esta
      trabalhando - evita o erro clasico de cadastrar no ambiente errado.
    -->
    <v-divider class="my-2"></v-divider>

    <div class="pa-3">
      <div class="text-caption text--secondary">
        <v-icon small>mdi-database</v-icon>
        PostgreSQL &middot; Spring Boot &middot; Vue
      </div>
      <div class="text-caption text--secondary mt-1">
        API: {{ enderecoApi }}
      </div>
    </div>
  </v-list>
</template>

<script>
export default {
  name: 'AppMenu',

  data() {
    return {
      /**
       * Itens do menu.
       * "rota" e o caminho da URL (o vue-router coloca o "#" antes).
       */
      itens: [
        {
          rota: '/unidades',
          titulo: 'Unidades',
          descricao: 'Cadastro e edicao',
          icone: 'mdi-office-building'
        },
        {
          rota: '/empresas',
          titulo: 'Empresas',
          descricao: 'Consulta de participantes',
          icone: 'mdi-domain'
        },
        {
          rota: '/leiloes',
          titulo: 'Leiloes',
          descricao: 'Consulta e totais',
          icone: 'mdi-gavel'
        }
      ],

      // Endereco da API (mostrado no rodape do menu).
      enderecoApi: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081'
    };
  }
};
</script>
