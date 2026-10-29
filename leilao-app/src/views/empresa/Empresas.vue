<template>
  <!--
    ==========================================================================
     VIEW: EMPRESAS   (views/empresa/Empresas.vue)
     EXERCICIO 6 - "tela de consulta parametrizada para cadastro de Empresas.
     Uma grid somente leitura (v-data-table) contendo os campos: cnpj,
     razaoSocial, telefone e email."

     EXERCICIO 8 (parte final) - "Nao esqueça de criar os links para editar
     e excluir uma empresa na tela de consulta feita no exercicio 6.
     O link de editar deve chamar a tela de cadastro usando a url #/empresa/:id"

     URL da pagina.....: http://server/#/empresas
     URL dos servicos..: http://server/empresas (GET)
    ==========================================================================
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
          BOTAO "NOVA EMPRESA".
          Leva para a tela de cadastro (exercicio 8) na URL #/empresa.
          Este botao NAO faz POST direto: ele navega para o formulario,
          porque a validacao e o preenchimento das mascaras acontecem la.
        -->
        <v-btn color="primary" depressed :to="'/empresa'">
          <v-icon left>mdi-plus</v-icon>
          Nova empresa
        </v-btn>
      </v-card-title>

      <!--
        TABELA SOMENTE LEITURA (conforme o enunciado).
        Nao ha edicao inline aqui: a edicao acontece na tela de cadastro.
      -->
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

        <!-- CNPJ com mascara (a API devolve so os digitos; a mascara e
             aplicada na exibicao para o usuario ler com ease). -->
        <template v-slot:item.cnpj="{ item }">
          {{ mascaraCnpj(item.cnpj) }}
        </template>

        <!-- Telefone com mascara. -->
        <template v-slot:item.telefone="{ item }">
          {{ mascaraTelefone(item.telefone) }}
        </template>

        <!-- CEP com mascara. -->
        <template v-slot:item.cep="{ item }">
          {{ item.cep ? mascaraCep(item.cep) : '-' }}
        </template>

        <!--
          COLUNA DE ACOES: os links de editar e excluir (exercicio 8).
          O de editar e um LINK DE PAGINA (v-btn com ":to"): ao clicar,
          a URL vira #/empresa/:id e a tela de cadastro abre com os dados
          ja carregados.
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
import api from '../../services/api';
import ConfirmDialog from '../../components/ConfirmDialog.vue';
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
      empresas: [],
      search: '',
      carregando: false,
      paraExcluir: null
    };
  },

  computed: {
    /**
     * Colunas da tabela.
     * O enunciado pede exatamente: cnpj, razaoSocial, telefone e email.
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

  mounted() {
    this.carregar();
  },

  methods: {
    /**
     * Carrega a lista de empresas.
     */
    async carregar() {
      this.carregando = true;
      try {
        this.empresas = await api.get('/empresas');
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao carregar as empresas.');
      } finally {
        this.carregando = false;
      }
    },

    // Mascaradores importados de utils/mascaras.js, usados na exibicao.
    mascaraCnpj: formatarCnpj,
    mascaraTelefone: formatarTelefone,
    mascaraCep: formatarCep,

    /**
     * Abre o dialogo de confirmacao.
     */
    pedirExclusao(item) {
      this.paraExcluir = item;
      this.$refs.dialogoExclusao.abrir();
    },

    /**
     * Exclui a empresa confirmada.
     */
    async confirmarExclusao() {
      if (!this.paraExcluir) {
        return;
      }

      try {
        await api.delete('/empresas/' + this.paraExcluir.id);
        this.notificar('success', 'Empresa excluida com sucesso.');
        this.paraExcluir = null;
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao excluir a empresa.');
      }
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
