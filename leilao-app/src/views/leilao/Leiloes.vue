<template>
  <!--
     Leiloes (views/leilao/Leiloes.vue).

     Exercicio 7 - consulta dos leiloes em grid somente leitura, com a
     razao social do vendedor, o inicio previsto e o total.

     Pagina:  #/leiloes
     Servico: GET /leiloes

     De onde nasce o "total"?

     O total nao e um campo da tabela tb_leilao: ele e calculado no banco, na
     consulta do repositorio (LeilaoRepository.findResumoLeiloes):

         total = SUM(lote.quantidade * lote.valor_inicial)

     agrupado por leilao. Assim o navegador recebe o total pronto, e o
     calculo nao depende de o frontend saber a regra de negocio (que
     poderia mudar sem ninguem avisar a tela).
  -->
  <div>
    <h1 class="titulo-tela">Leiloes</h1>
    <p class="subtitulo-tela">
      Leiloes cadastrados, com o vendedor, o inicio previsto e o valor total
      dos lotes.
    </p>

    <!--
      Cards de resumo: os numeros que o usuario mais procura.
    -->
    <v-row dense class="mb-4">
      <v-col cols="12" sm="4" md="3">
        <v-card outlined class="pa-4">
          <div class="text-caption text--secondary">Quantidade de leiloes</div>
          <div class="text-h4 font-weight-bold" style="color: var(--az-azul-900);">
            {{ leiloes.length }}
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" sm="8" md="5">
        <v-card outlined class="pa-4">
          <div class="text-caption text--secondary">Valor total Involved</div>
          <div class="text-h4 font-weight-bold" style="color: var(--az-azul-900);">
            {{ formatarDinheiro(valorTotalGeral) }}
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" md="4">
        <v-card outlined class="pa-4">
          <div class="text-caption text--secondary">Proximo leilao</div>
          <div class="text-h6 font-weight-medium">
            {{ proximoLeilao ? formatarData(proximoLeilao.inicioPrevisto) : '-' }}
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-card elevation="2">
      <v-card-title>
        <v-text-field
          v-model="search"
          prepend-inner-icon="mdi-magnify"
          label="Buscar por codigo, descricao ou vendedor"
          single-line
          hide-details
          clearable
          dense
          outlined
          style="max-width: 420px;"
        />

        <v-spacer></v-spacer>

        <v-btn color="primary" depressed @click="carregar" :loading="carregando">
          <v-icon left>mdi-refresh</v-icon>
          Atualizar
        </v-btn>
      </v-card-title>

      <!-- Tabela somente leitura. -->
      <v-data-table
        :headers="headers"
        :items="leiloes"
        :search="search"
        :loading="carregando"
        item-key="id"
        :items-per-page="10"
        class="elevation-0"
      >
        <template v-slot:item.codigo="{ item }">
          <strong>#{{ item.codigo }}</strong>
        </template>

        <template v-slot:item.descricao="{ item }">
          {{ item.descricao || '-' }}
        </template>

        <!-- vendedor (razao social) -->
        <template v-slot:item.razaoSocial="{ item }">
          {{ item.razaoSocial || '-' }}
        </template>

        <!-- inicio previsto (data no padrao brasileiro) -->
        <template v-slot:item.inicioPrevisto="{ item }">
          {{ item.inicioPrevisto ? formatarData(item.inicioPrevisto) : '-' }}
        </template>

        <!-- total (dinheiro no padrao brasileiro: R$ 80.000,00) -->
        <template v-slot:item.total="{ item }">
          <span class="font-weight-medium">{{ formatarDinheiro(item.total) }}</span>
        </template>

        <template v-slot:no-data>
          <div class="pa-6 text-center grey--text">
            <v-icon size="48" color="grey lighten-1">mdi-gavel</v-icon>
            <p class="mt-3 mb-0">Nenhum leilao cadastrado.</p>
          </div>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
/**
 * Script da tela de leiloes.
 *
 * O total nao e calculado no navegador: vem de uma consulta no banco (veja
 * o LeilaoRepository), e por isso que a tela so faz leitura.
 */

import api from '../../services/api';

export default {
  name: 'TelaLeiloes',

  data() {
    return {
      // Os leiloes vindas da API.
      leiloes: [],

      // Texto da busca (filtro client side do v-data-table).
      search: '',

      // Controla a barra de progresso da tabela.
      carregando: false
    };
  },

  computed: {
    /**
     * Colunas da tabela: exatamente os campos pedidos no exercicio 7.
     * "total" vem calculada do banco.
     */
    headers() {
      return [
        { text: 'Codigo', value: 'codigo', width: 110 },
        { text: 'Descricao', value: 'descricao' },
        { text: 'Vendedor', value: 'razaoSocial' },
        { text: 'Inicio previsto', value: 'inicioPrevisto', width: 190 },
        { text: 'Total', value: 'total', align: 'end', width: 160 }
      ];
    },

    /**
     * Soma de todos os totais, pro card de resumo.
     *
     * O "reduce" percorre a lista somando, comecando em 0 (o segundo
     * parametro). O "Number(leilao.total) || 0" protege contra valor nulo
     * ou invalido, que entraria na soma como NaN e corromperia o total
     * inteiro.
     *
     * Repare que o resultado nao fica em "data": ele e derivado da lista.
     * Guardando os dois, teriamos dois valores pra manter em sincronia, e
     * no dia em que isso falha o usuario ve um total errado na tela.
     */
    valorTotalGeral() {
      return this.leiloes.reduce(
        (soma, leilao) => soma + (Number(leilao.total) || 0),
        0
      );
    },

    /**
     * O proximo leilao a comecar (o de inicio previsto mais proximo).
     * Lista vazia nao tem proximo; leiloes sem data ficam de fora, porque
     * nao podem ser comparados por data.
     */
    proximoLeilao() {
      if (this.leiloes.length === 0) {
        return null;
      }

      const comData = this.leiloes
        .filter(leilao => !!leilao.inicioPrevisto)
        .sort((a, b) => new Date(a.inicioPrevisto) - new Date(b.inicioPrevisto));

      return comData.length > 0 ? comData[0] : null;
    }
  },

  mounted() {
    this.carregar();
  },

  methods: {
    /**
     * Carrega (ou recarrega) os leiloes. O "finally" garante que o
     * indicador de carregamento saia da tela mesmo em caso de erro.
     */
    async carregar() {
      this.carregando = true;
      try {
        this.leiloes = await api.get('/leiloes');
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao carregar os leiloes.');
      } finally {
        this.carregando = false;
      }
    },

    /**
     * Formata um valor como dinheiro brasileiro.
     * Ex.: 80000 -> "R$ 80.000,00"
     *
     * Usamos a API nativa "Intl.NumberFormat", que ja conhece o padrao
     * brasileiro. Escrever essa formatacao na mao seria trabalho inutil.
     *
     * @param {number|string} valor valor a formatar
     * @returns {string} o valor no padrao brasileiro
     */
    formatarDinheiro(valor) {
      const numero = Number(valor);

      // "isFinite" devolve false para NaN, Infinity e valores nao
      // numericos. Nesse caso, mostramos zero em vez de "R$ NaN".
      if (!isFinite(numero)) {
        return 'R$ 0,00';
      }

      return new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL'
      }).format(numero);
    },

    /**
     * Formata a data no padrao do navegador do usuario.
     *
     * @param {string} valor data em formato ISO
     * @returns {string} a data formatada, ou "-" se nao houver data
     */
    formatarData(valor) {
      if (!valor) {
        return '-';
      }
      return new Date(valor).toLocaleString('pt-BR');
    },

    // Atalho pra exibir a notificacao global.
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
