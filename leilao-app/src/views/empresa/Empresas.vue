<template>
  <!--
     Empresas (views/empresa/Empresas.vue).

     Exercicio 6 - consulta parametrizada do cadastro, em grid somente
     leitura com cnpj, razaoSocial, telefone e email.

     Exercicio 8 (parte final) - os links de editar e excluir. A edicao
     chama a tela de cadastro pela URL #/empresa/:id.

     Pagina:  #/empresas
     Servico: GET /empresas
  -->
  <div>
    <h1 class="titulo-tela">Empresas</h1>
    <p class="subtitulo-tela">
      Empresas participantes dos leiloes - vendedoras e compradoras.
    </p>

    <v-card elevation="2">
      <v-card-title>
        <v-text-field
          v-model="search"
          prepend-inner-icon="mdi-magnify"
          label="Buscar por razao social, CNPJ, telefone ou e-mail"
          single-line
          hide-details
          clearable
          dense
          outlined
          style="max-width: 420px;"
        />

        <v-spacer></v-spacer>

        <!--
          Botao "Nova empresa". Ele nao faz POST direto: navega pro
          formulario, porque a validacao e as mascaras acontecem la.
        -->
        <v-btn color="primary" depressed :to="'/empresa'">
          <v-icon left>mdi-plus</v-icon>
          Nova empresa
        </v-btn>
      </v-card-title>

      <!-- Tabela somente leitura. A edicao acontece na tela de cadastro. -->
      <v-data-table
        :headers="headers"
        :items="empresas"
        :search="search"
        :loading="carregando"
        item-key="id"
        :items-per-page="10"
        class="elevation-0"
      >
        <template v-slot:header.actions>
          <span class="text-right">Acoes</span>
        </template>

        <!-- CNPJ mascarado: a API devolve so os digitos. -->
        <template v-slot:item.cnpj="{ item }">
          {{ mascaraCnpj(item.cnpj) }}
        </template>

        <template v-slot:item.telefone="{ item }">
          {{ mascaraTelefone(item.telefone) }}
        </template>

        <template v-slot:item.cep="{ item }">
          {{ item.cep ? mascaraCep(item.cep) : '-' }}
        </template>

        <!--
          Coluna de acoes (exercicio 8). O botao de editar e um link de
          pagina: ao clicar a URL vira #/empresa/:id e a tela de cadastro
          abre com os dados ja carregados.
        -->
        <template v-slot:item.actions="{ item }">
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn
                icon
                color="primary"
                :to="'/empresa/' + item.id"
                v-bind="attrs"
                v-on="on"
                title="Editar"
              >
                <v-icon>mdi-pencil</v-icon>
              </v-btn>
            </template>
            <span>Editar empresa</span>
          </v-tooltip>

          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn
                icon
                color="error"
                @click="pedirExclusao(item)"
                v-bind="attrs"
                v-on="on"
                title="Excluir"
              >
                <v-icon>mdi-delete</v-icon>
              </v-btn>
            </template>
            <span>Excluir empresa</span>
          </v-tooltip>
        </template>

        <template v-slot:no-data>
          <div class="pa-6 text-center grey--text">
            <v-icon size="48" color="grey lighten-1">mdi-domain</v-icon>
            <p class="mt-3 mb-0">Nenhuma empresa cadastrada.</p>
          </div>
        </template>
      </v-data-table>
    </v-card>

    <confirm-dialog
      ref="dialogoExclusao"
      titulo="Excluir empresa"
      :mensagem="'Deseja realmente excluir a empresa \'' + (paraExcluir ? paraExcluir.razaoSocial : '') + '\'? Esta acao nao pode ser desfeita.'"
      @confirmar="confirmarExclusao"
    />
  </div>
</template>

<script>
/**
 * Script da tela de empresas.
 *
 * Consulta somente leitura das empresas participantes, com links pra editar
 * e excluir.
 *
 * CNPJ, telefone e CEP aparecem mascarados na tela, mas sao gravados sem
 * pontuacao no banco: exibicao nao e armazenamento.
 */

// O axios e criado e configurado uma vez em src/services/api.js. As telas
// nunca chamam o axios direto - usam este "api", que ja tem o endereco
// base, o timeout e o tratamento de erro padronizado.
import api from '../../services/api';

import ConfirmDialog from '../../components/ConfirmDialog.vue';

// Os mascaradores vem de utils/mascaras.js. O "as" renomeia na importacao
// pra deixar claro aqui que sao formatadores de exibicao.
import {
  mascaraCnpj as formatarCnpj,
  mascaraTelefone as formatarTelefone,
  mascaraCep as formatarCep
} from '../../utils/mascaras';

export default {
  name: 'TelaEmpresas',

  components: {
    ConfirmDialog
  },

  data() {
    return {
      // Lista de empresas vinda da API. Comeca como array vazio (e nao
      // null) porque e o que o Vuetify espera, e e o estado natural de
      // "ainda nao carregou".
      empresas: [],

      // Texto da busca. O v-data-table filtra sozinho por este valor, e
      // no navegador - rapido para uma lista desse tamanho e sem trafego
      // na rede.
      search: '',

      // Enquanto for true, o Vuetify mostra o indicador de carregamento.
      carregando: false,

      // A empresa marcada pra exclusao. Fica guardada aqui enquanto o
      // dialogo de confirmacao esta aberto, e e o que permite saber qual
      // registro apagar.
      paraExcluir: null
    };
  },

  computed: {
    /**
     * Colunas da tabela. O enunciado do exercicio 6 pede exatamente
     * cnpj, razaoSocial, telefone e email; a coluna de acoes vem do
     * exercicio 8.
     *
     * "value" e o campo que o Vuetify le em cada linha. A coluna
     * "actions" nao existe como campo: ela e um slot, um pedaco de
     * template customizado (veja o v-slot:item.actions acima).
     */
    headers() {
      return [
        { text: 'CNPJ', value: 'cnpj', width: 170 },
        { text: 'Razao social', value: 'razaoSocial' },
        { text: 'Telefone', value: 'telefone', width: 160 },
        { text: 'E-mail', value: 'email' },
        { text: 'Acoes', value: 'actions', sortable: false, align: 'end', width: 110 }
      ];
    }
  },

  /**
   * "mounted" roda quando o componente ja esta na tela. E o lugar certo
   * pra buscar dados: o usuario ve o indicador de carregamento enquanto a
   * chamada acontece. No "created" a tela ficaria em branco por um
   * instante.
   */
  mounted() {
    this.carregar();
  },

  methods: {
    /**
     * Carrega (ou recarrega) a lista de empresas.
     *
     * O "finally" e o que garante que o indicador de carregamento saia da
     * tela mesmo quando a chamada falha.
     */
    async carregar() {
      this.carregando = true;
      try {
        this.empresas = await api.get('/empresas');
      } catch (erro) {
        // O interceptor do axios (services/api.js) ja traduziu o erro do
        // backend pra { status, mensagem, campos }.
        this.notificar('error', erro.mensagem || 'Erro ao carregar as empresas.');
      } finally {
        this.carregando = false;
      }
    },

    // Mascaradores de utils/mascaras.js. Atribuidos como metodos pra
    // poderem ser chamados no template.
    mascaraCnpj: formatarCnpj,
    mascaraTelefone: formatarTelefone,
    mascaraCep: formatarCep,

    // Guarda o registro escolhido antes de abrir o dialogo, pra confirmacao
    // saber o que apagar.
    pedirExclusao(item) {
      this.paraExcluir = item;
      this.$refs.dialogoExclusao.abrir();
    },

    // Executa a exclusao (o usuario ja confirmou no dialogo).
    async confirmarExclusao() {
      if (!this.paraExcluir) {
        return;
      }

      try {
        await api.delete('/empresas/' + this.paraExcluir.id);
        this.notificar('success', 'Empresa excluida com sucesso.');
        this.paraExcluir = null;
        // Recarrega a lista pra linha apagada sumir da tabela.
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao excluir a empresa.');
      }
    },

    // Atalho pra exibir a notificacao global, sem repetir o caminho
    // "$root.$refs.notificacoes.exibir(...)" em cada chamada.
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
