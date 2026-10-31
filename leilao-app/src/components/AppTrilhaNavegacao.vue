<template>
  <!--
    Trilha de navegação (components/AppTrilhaNavegacao.vue).

    E' o "caminho" que o usuario percorreu para chegar na tela: "Inicio >
    Unidades > Detalhe". Aparece na barra superior.

    Por que isso importa, alem da estetica:

    1. **Orientacao.** O endereco "/empresa/7" nao diz nada. A trilha diz
       em que parte da aplicacao a pessoa esta.

    2. **Acessibilidade.** Um leitor de tela announce a trilha como uma
       "navigation" (landmark) e o item atual como "current page". Sem o
       "aria-current", o usuario de leitor de tela nao sabe qual item da
       trilha e' o atual - e pode tentar clicar num item que ja' esta.

    3. **Atalho.** Cada item anterior e' clicavel e devolve a pessoa
       para aquele nivel.
  -->
  <nav
    class="trilha"
    :aria-label="rotuloNavegacao"
  >
    <!--
      A lista e' a que os leitores de tela usam para navegar: cada item
      e' anunciado junto com a sua posicao ("3 de 4").
    -->
    <ol class="trilha-lista">
      <li
        v-for="(item, indice) in itens"
        :key="item.chave"
        class="trilha-item"
      >
        <!--
          Separador entre os itens. O "aria-hidden" e' importante: ele e'
          puramente visual, e sem isso o leitor de tela anunciaria
          "seta para a direita" entre cada item - ruido puro.
        -->
        <v-icon
          v-if="indice > 0"
          class="trilha-separador"
          size="16"
          aria-hidden="true"
        >
          mdi-chevron-right
        </v-icon>

        <!--
          O item atual. O "aria-current='page'" diz ao leitor de tela
          "voce esta aqui". Visualmente, ele fica com peso maior e sem
          link (nao ha para onde voltar).
        -->
        <span
          v-if="item.atual"
          class="trilha-atual"
          aria-current="page"
        >
          {{ item.texto }}
        </span>

        <!--
          Os itens anteriores: links de verdade, que funcionam com Tab e
          Enter, e abrem em nova aba com Ctrl+Clique.
        -->
        <router-link
          v-else
          class="trilha-link"
          :to="item.rotas"
        >
          {{ item.texto }}
        </router-link>
      </li>
    </ol>
  </nav>
</template>

<script>
/**
 * Script da trilha de navegacao.
 *
 * A trilha e' montada a partir da rota atual e do mapa de nomes de rota.
 * Isso evita duplicar a arvore de telas em dois lugares: quando uma tela
 * nova entra no menu, so o mapa precisa mudar.
 */
export default {
  name: 'AppTrilhaNavegacao',

  props: {
    /**
     * Rotulo do "landmark" de navegacao.
     *
     * O leitor de tela anuncia "navigation" - e o rotulo diz qual
     * navegacao e'. Um rotulo generico ("navigation") em varias areas da
     * tela e' confuso para quem navega por landmarks.
     */
    rotuloNavegacao: {
      type: String,
      default: 'Trilha de navegação'
    }
  },

  computed: {
    /**
     * Os itens da trilha, do mais geral ao mais especifico.
     *
     * Cada item tem:
     *   - texto: o que aparece;
     *   - rotas: o caminho do vue-router (array, para link externo);
     *   - atual: se e' a pagina que a pessoa esta vendo.
     */
    itens() {
      const rota = this.$route;

      const itens = [{
        texto: 'Início',
        // A RAIZ da aplicacao ("/"), e nao "/unidades".
        //
        // Dois motivos:
        //
        // 1. O vue-router marca com "aria-current='page'" o link cuja
        //    rota bate com a rota atual. Se o "Inicio" apontasse para
        //    "/unidades", ele seria marcado como atual em toda tela de
        //    unidades - e a trilha teria dois "atuais", o que faz o
        //    leitor de tela anunciar a pagina atual duas vezes.
        //
        // 2. A raiz redireciona para unidades. Clicar no "Inicio"
        //    leva a pessoa para a tela inicial do sistema, e nao
        //    apenas para a rota que ela ja' esta' vendo.
        rotas: '/',
        chave: 'inicio',
        atual: false
      }];

      // A rota "raiz" e' um redirecionamento para unidades: quando ela
      // aparece na URL, a trilha mostra so "Inicio" (que ja' aponta
      // para a tela inicial). Sem isso, a pessoa veria "Inicio >
      // Unidades" sem ter ido a lugar nenhum.
      if (!rota.matched.length || rota.path === '/') {
        return itens;
      }

      const titulos = this.titulosDasRotas;

      // Um registro por nivel da rota. O "path" de cada registro ja' e' o
      // caminho completo ("/empresa/7"), entao nao ha montagem a fazer.
      rota.matched.forEach((registro, indice) => {
        const titulo = titulos[registro.name] || registro.name || '';

        if (!titulo) {
          return;
        }

        itens.push({
          texto: titulo,
          rotas: registro.path,
          chave: 'rota-' + (registro.name || indice),
          atual: indice === rota.matched.length - 1
        });
      });

      // A trilha e' sempre o caminho COMPLETO: "Inicio > Unidades",
      // "Inicio > Editar empresa".
      //
      // O "Inicio" aponta para a tela inicial de unidades - que e' onde
      // a pessoa cai quando abre a aplicacao. Por isso, na tela de
      // unidades, a trilha mostra "Inicio > Unidades": sao dois niveis
      // reais (a aplicacao, e a tela dentro dela), e nao uma repeticao
      // sem informacao.
      //
      // O que NAO se faz e' esconder o "Inicio" quando a tela atual e' a
      // inicial. Isso quebraria o padrao mental de quem usa leitor de
      // tela: a trilha sempre comeca no mesmo lugar, e o numero de
      // niveis diz o quao fundo a pessoa esta.
      return itens;
    },

    /**
     * O nome amigavel de cada rota.
     *
     * A chave e' o "name" da rota no vue-router; o valor, o texto que a
     * pessoa le.
     */
    titulosDasRotas() {
      return {
        Unidades: 'Unidades',
        Empresas: 'Empresas',
        Leiloes: 'Leilões',
        EmpresaIncluir: 'Nova empresa',
        EmpresaEditar: 'Editar empresa'
      };
    }
  }
};
</script>

<style scoped>
/**
 * A trilha fica na barra superior, com os itens em linha.
 */
.trilha {
  display: flex;
  align-items: center;
  min-width: 0;
}

/**
 * A lista e' horizontal, sem os marcadores de lista padrao (que o
 * leitor de tela anuncia como "item de lista" - o que seria correto,
 * mas o espaco visual e' desnecessario).
 */
.trilha-lista {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trilha-item {
  display: flex;
  align-items: center;
  min-width: 0;
}

/**
 * O separador entre os itens. Escondido do leitor de tela (aria-hidden
 * no template), porque ele nao carrega informacao.
 */
.trilha-separador {
  margin: 0 4px;
  opacity: 0.6;
  flex-shrink: 0;
}

/**
 * Um item anterior da trilha: link.
 *
 * A cor e' a de link do tema (laranja), que passa no contraste exigido
 * pela WCAG AA para texto normal.
 */
.trilha-link {
  color: var(--az-laranja-600);
  text-decoration: none;
  white-space: nowrap;
  padding: 2px 4px;
  border-radius: var(--az-raio-sm);
}

.trilha-link:hover {
  text-decoration: underline;
}

/**
 * O foco de teclado precisa ser visivel. Sem isto, quem navega pelo
 * teclado nao sabe onde esta.
 */
.trilha-link:focus-visible {
  outline: 2px solid var(--az-laranja-500);
  outline-offset: 2px;
}

/**
 * O item atual: mais peso, sem link.
 */
.trilha-atual {
  font-weight: 600;
  white-space: nowrap;
  padding: 2px 4px;
  color: var(--az-branco);
}

/**
 * Em telas pequenas, a trilha some (nao ha espaco) - mas o titulo da
 * tela continua no "<h1>", que e' o que o leitor de tela usa.
 */
@media (max-width: 600px) {
  .trilha {
    display: none;
  }
}
</style>
