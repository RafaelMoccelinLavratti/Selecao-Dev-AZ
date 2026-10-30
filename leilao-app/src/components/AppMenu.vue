<template>
  <!--
     Menu de navegacao.

     Fica num componente proprio (e nao dentro do App.vue) pra deixar o
     App.vue enxuto, permitir reuso e porque o enunciado pede "separe bem
     o codigo em componentes".

     O menu usa <router-link>, que e o atalho do vue-router: desenha um link
     de verdade (dá pra abrir em nova aba e copiar a URL) e, ao clicar,
     troca a tela E a URL.

     Este componente tem um unico elemento raiz (v-list). O Vue 2 exige isso:
     um template precisa ter exatamente um elemento raiz.
  -->
  <v-list nav dense>
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
      Separador + informacao da conexao. Mostrar com qual ambiente a
      pessoa esta trabalhando evita o erro classico de cadastrar no banco
      errado sem perceber.
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
/**
 * Script do componente de menu.
 *
 * O menu lista as telas a partir da lista de rotas do router. Ele existe
 * separado porque deixa o App.vue enxuto e permite marcar o item ativo.
 */
export default {
  name: 'AppMenu',

  data() {
    return {
      // Itens do menu. Declarar como dados (e nao escrever cada link no
      // template) e o que deixa a lista crescer sem mexer no HTML.
      itens: [
        {
          // Consulta e edicao de unidades (exercicio 5).
          rota: '/unidades',
          titulo: 'Unidades',
          descricao: 'Cadastro e edicao',
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
          titulo: 'Leiloes',
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
