<template>
  <!--
    ==========================================================================
     VIEW: LEILOES   (views/leilao/Leiloes.vue)
     EXERCICIO 7 - "tela de consulta parametrizada para cadastro de Leiloes.
     Uma grid somente leitura (v-data-table) contendo os campos: razaoSocial
     do vendedor, inicioPrevisto, total do leilao (soma dos totais
     individuais de cada lote (quantidade * valorInicial))."

     URL da pagina.....: http://server/#/leiloes
     URL dos servicos..: http://server/leiloes (GET)

     ------------------------------------------------------
     ONDE NASCE O "TOTAL"?
     ------------------------------------------------------
     O total NAO e um campo da tabela tb_leilao - ele e CALCULADO. O
     trabalho acontece no banco de dados, na consulta do repositorio
     (LeilaoRepository.findResumoLeiloes), que faz:

         total = SUM(lote.quantidade * lote.valor_inicial)

     grouped por leilao. Assim o navegador recebe o total pronto, e o
     calculo nao depende de o frontend saber a regra de negocio (que
     poderia mudar sem ninguem avisar a tela).
    ==========================================================================
  -->
  <div>
    <h1 class="titulo-tela">Leiloes</h1>
    <p class="subtitulo-tela">
      Leiloes cadastrados, com o vendedor, o inicio previsto e o valor total
      dos lotes.
    </p>

    <!--
      CARDS DE RESUMO (acima da tabela).
      Sao os numeros que o usuario mais procura: quantos leiloes existem e
      qual o valor total emvolvedido.
    -->
    <v-row dense class="mb-4">
      <v-col cols="12" sm="4" md="3">
        <v-card outlined class="pa-4">
          <div class="text-caption text--secondary">Quantidade de leiloes</div>
          <div class="text-h4 font-weight-bold" style="color: #0f4c75;">
            {{ leiloes.length }}
          </div>
        </v-card>
      </v-col>

      <v-col cols="12" sm="8" md="5">
        <v-card outlined class="pa-4">
          <div class="text-caption text--secondary">Valor total Involved</div>
          <div class="text-h4 font-weight-bold" style="color: #0f4c75;">
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

      <!--
        TABELA SOMENTE LEITURA (conforme o enunciado).
      -->
      <v-data-table
        :headers="headers"
        :items="leiloes"
        :search="search"
        :loading="carregando"
        item-key="id"
        :items-per-page="10"
        class="elevation-0"
      >
        <!-- CODIGO do leilao -->
        <template v-slot:item.codigo="{ item }">
          <strong>#{{ item.codigo }}</strong>
        </template>

        <!-- DESCRICAO -->
        <template v-slot:item.descricao="{ item }">
          {{ item.descricao || '-' }}
        </template>

        <!-- VENDEDOR (razao social) -->
        <template v-slot:item.razaoSocial="{ item }">
          {{ item.razaoSocial || '-' }}
        </template>

        <!-- INICIO PREVISTO (data formatada no padrao brasileiro) -->
        <template v-slot:item.inicioPrevisto="{ item }">
          {{ item.inicioPrevisto ? formatarData(item.inicioPrevisto) : '-' }}
        </template>

        <!-- TOTAL (dinheiro no padrao brasileiro: R$ 80.000,00) -->
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
import api from '../../services/api';

export default {
  name: 'TelaLeiloes',

  data() {
    return {
      leiloes: [],
      search: '',
      carregando: false
    };
  },

  computed: {
    /**
     * Colunas da tabela: exatamente os campos pedidos no exercicio 7.
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
     * Soma de todos os totais (para o card de resumo).
     */
    valorTotalGeral() {
      return this.leiloes.reduce(
        (soma, leilao) => soma + (Number(leilao.total) || 0),
        0
      );
    },

    /**
     * O proximo leilao a comecar (o de inicio previsto mais proximo).
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
     * Carrega os leiloes.
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
     */
    formatarDinheiro(valor) {
      const numero = Number(valor);
      if (!isFinite(numero)) {
        return 'R$ 0,00';
      }
      return new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL'
      }).format(numero);
    },

    /**
     * Formata a data no padrao brasileiro.
     */
    formatarData(valor) {
      if (!valor) {
        return '-';
      }
      return new Date(valor).toLocaleString('pt-BR');
    },

    /**
     * Atalho para exibir a notificacao global.
     */
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
