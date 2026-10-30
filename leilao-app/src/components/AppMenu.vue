<template>
  <!--
     COMPONENTE: MENU DE NAVEGACAO  (components/AppMenu.vue)
     Fica em um componente proprio (e nao dentro do App.vue) porque:
       - o App.vue fica enxuto e legivel;
       - o menu pode ser reutilizado em outras telas;
       - o enunciado pede "Separe bem o codigo em componentes".

     O menu usa <router-link>, que e o "atalho" do vue-router: ele
     desenha um link de verdade (pode abrir em nova aba, copiar URL) e
     ao clicar, troca a tela E a URL para #/unidades, #/empresas, #/leiloes.

     Este componente tem UM unico elemento raiz (v-list). O Vue 2 exige
     isso: um template precisa ter exatamente um elemento raiz.
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
      Separador + informacao da conexao.
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
/**
 * Script do componente de menu.
 *
 * O menu lista as telas a partir da lista de rotas do router. Ele existe
 * separado porque deixa o App.vue enxuto e permite marcar o item ativo.
 */
export default {
  /**
   * Nome do componente.
   *
   * Em Vue 2, um componente tambem precisa de um "name" para poder ser
   * usado em codigo (por exemplo, dentro de um <router-view> ou
   * referenciado pelo nome no template de outro componente).
   */
  name: 'AppMenu',

  /**
   *  DADOS REATIVOS DO COMPONENTE (data).
   *
   *  Tudo que estiver aqui e REATIVO: quando um valor muda, o Vue
   *  redesenha automaticamente os trechos de template que dependem dele.
   *
   *  Por isso, dados que nunca mudam (como a lista de itens do menu)
   *  nao precisam estar em "data" - poderiam ser uma constante do
   *  modulo. Mas deixar aqui deixa o componente mais simples de
   *  entender e de estender (por exemplo, se um dia o menu for montado
   *  a partir da API, o lugar ja existe).
   */
  data() {
    return {
      /**
       * Itens do menu (um objeto por item).
       *
       * Cada item tem quatro campos:
       *   rota      : o caminho da URL (o vue-router coloca o "#" antes);
       *   titulo    : o texto principal do link;
       *   descricao : o texto auxiliar (aparece em cinza, embaixo);
       *   icone     : o nome do icone do Material Design Icons.
       *
       * Declarar os itens como DADOS (e nao escrever cada link
       * diretamente no template) e o que permite que a lista cresca
       * sem mexer no HTML.
       */
      itens: [
        {
          // Tela de consulta e edicao de unidades (exercicio 5).
          rota: '/unidades',
          titulo: 'Unidades',
          descricao: 'Cadastro e edicao',
          icone: 'mdi-office-building'
        },
        {
          // Tela de consulta de empresas (exercicio 6).
          rota: '/empresas',
          titulo: 'Empresas',
          descricao: 'Consulta de participantes',
          icone: 'mdi-domain'
        },
        {
          // Tela de consulta de leiloes (exercicio 7).
          rota: '/leiloes',
          titulo: 'Leiloes',
          descricao: 'Consulta e totais',
          icone: 'mdi-gavel'
        }
      ],

      /**
       * Endereco da API, mostrado no rodape do menu.
       *
       * Vem da variavel de ambiente VUE_APP_API_BASE_URL (definida no
       * arquivo .env da pasta leilao-app). O "||" (OU) abaixo e um
       * plano B: se a variavel nao existir, usa o endereco local padrao.
       *
       * Por que mostrar isso na tela?
       * Para o usuario (e para quem avalia o sistema) saber com
       * qual ambiente esta trabalhando. evita o erro classico de
       * cadastrar dados no banco errado sem perceber.
       */
      enderecoApi: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8081'
    };
  }
};
</script>
