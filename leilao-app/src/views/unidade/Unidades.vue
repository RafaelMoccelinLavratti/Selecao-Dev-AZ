<template>
  <!--
    ==========================================================================
     VIEW: UNIDADES   (views/unidade/Unidades.vue)
     EXERCICIO 5 - "tela de consulta e edicao simples para o cadastro de
     Unidade. Uma grid editavel capaz de realizar todas as operacoes CRUD
     na tabela de Unidade (v-data-table)."

    ==========================================================================

     URL da pagina......: http://server/#/unidades
     URL dos servicos...: http://server/unidades (GET | POST | PUT | DELETE)
     Componente..........: este arquivo + components/

     ------------------------------------------------------
     COMO A "GRID EDITAVEL" FUNCIONA
     ------------------------------------------------------
     Este e o padrao canonico de "tabela editavel" do Vuetify. A linha
     tem TRES estados possiveis:

       1. MODO CONSULTA - mostra os dados. No canto direito aparecem
          os icones "editar" (lapis) e "excluir" (lixeira).

       2. MODO EDICAO - ao clicar no lapis, a linha vira um FORMULARIO
          (os textos viram campos) e os icones viram "salvar" (check) e
          "cancelar" (X).

       3. LINHA DE INCLUSAO - quando o usuario clica em "Nova unidade",
          uma linha em branco aparece NO TOPO da tabela, pronta para
          preencher. Ao confirmar, ela vira um registro novo na API.

     Detalhe de implementacao: a lista exibida e um "computed" que
     coloca a linha de inclusao (quando existe) na frente da lista real.
     Assim nao existe uma segunda tabela na tela - e uma unica tabela,
     que muda de conteudo.
    ==========================================================================
  -->
  <div>
    <h1 class="titulo-tela">Unidades</h1>
    <p class="subtitulo-tela">
      Cadastro das unidades (locais) onde os leiloes sao realizados.
    </p>

    <v-card elevation="2">
      <!-- CABECALHO: busca + botoes -->
      <v-card-title>
        <v-text-field
          v-model="search"
          prepend-inner-icon="mdi-magnify"
          label="Buscar unidade"
          single-line
          hide-details
          clearable
          dense
          outlined
          style="max-width: 320px;"
        />

        <v-spacer></v-spacer>

        <v-btn color="success" depressed @click="comecarInclusao">
          <v-icon left>mdi-plus</v-icon>
          Nova unidade
        </v-btn>

        <v-btn
          color="primary"
          depressed
          @click="carregar"
          :loading="carregando"
          class="ml-2"
        >
          <v-icon left>mdi-refresh</v-icon>
          Atualizar
        </v-btn>
      </v-card-title>

      <!--
        TABELA.
        "headers"  = as colunas (definidas no metodo, no script).
        "items"    = os dados (a lista computada "linhasExibidas").
        "search"   = o texto do campo de busca (filtra automaticamente).
        "loading"  = mostra a barra de progresso enquanto carrega.
      -->
      <v-data-table
        :headers="headers"
        :items="linhasExibidas"
        :search="search"
        :loading="carregando"
        item-key="id"
        :items-per-page="10"
        class="elevation-0"
      >
        <!--
          CABECALHO CUSTOMIZADO: o titulo de "Acoes" fica alinhado a
          direita, junto com os botoes.
        -->
        <template v-slot:header.actions>
          <span class="text-right">Acoes</span>
        </template>

        <!--
          CELULA "NOME": quando a linha esta em edicao (ou e a linha de
          inclusao), vira um campo de edicao; quando nao, mostra o texto.
        -->
        <template v-slot:item.nome="{ item }">
          <!-- MODO EDICAO de um registro existente -->
          <v-edit-field
            v-if="editando === item.id"
            v-model="editado.nome"
            :rules="[regras.nome]"
            label="Nome"
            single-line
            dense
            @save="salvarEdicao"
            @cancel="cancelarEdicao"
          ></v-edit-field>

          <!-- MODO INCLUSAO (a linha nova do topo) -->
          <v-edit-field
            v-else-if="item.id === null"
            v-model="novo.nome"
            :rules="[regras.nome]"
            label="Nome da nova unidade"
            single-line
            dense
            autofocus
            @save="salvarNova"
            @cancel="cancelarInclusao"
          ></v-edit-field>

          <!-- MODO CONSULTA -->
          <span v-else>{{ item.nome }}</span>
        </template>

        <!--
          CELULA "ACOES": os botoes de editar e excluir.
          Quando a linha esta em edicao, viram salvar e cancelar.
        -->
        <template v-slot:item.actions="{ item }">
          <template v-if="editando === item.id">
            <v-btn icon color="success" title="Salvar" @click="salvarEdicao">
              <v-icon>mdi-check</v-icon>
            </v-btn>
            <v-btn icon color="warning" title="Cancelar" @click="cancelarEdicao">
              <v-icon>mdi-close</v-icon>
            </v-btn>
          </template>

          <template v-else-if="item.id === null">
            <v-btn icon color="success" title="Cadastrar" @click="salvarNova">
              <v-icon>mdi-check</v-icon>
            </v-btn>
            <v-btn icon color="warning" title="Cancelar" @click="cancelarInclusao">
              <v-icon>mdi-close</v-icon>
            </v-btn>
          </template>

          <template v-else>
            <v-btn icon color="primary" title="Editar" @click="comecarEdicao(item)">
              <v-icon>mdi-pencil</v-icon>
            </v-btn>
            <v-btn icon color="error" title="Excluir" @click="pedirExclusao(item)">
              <v-icon>mdi-delete</v-icon>
            </v-btn>
          </template>
        </template>

        <!--
          MENSAGEM QUANDO NAO HAI NENHUMA UNIDADE CADASTRADA.
        -->
        <template v-slot:no-data>
          <div class="pa-6 text-center grey--text">
            <v-icon size="48" color="grey lighten-1">mdi-office-building-outline</v-icon>
            <p class="mt-3 mb-0">Nenhuma unidade cadastrada.</p>
            <p class="mb-0" style="font-size: 0.85rem;">
              Clique em "Nova unidade" para cadastrar a primeira.
            </p>
          </div>
        </template>
      </v-data-table>
    </v-card>

    <!-- DIALOGO DE CONFIRMACAO DE EXCLUSAO -->
    <confirm-dialog
      ref="dialogoExclusao"
      titulo="Excluir unidade"
      :mensagem="'Deseja realmente excluir a unidade \'' + (paraExcluir ? paraExcluir.nome : '') + '\'? Esta acao nao pode ser desfeita.'"
      @confirmar="confirmarExclusao"
    />
  </div>
</template>

<script>
/**
 * Importacao da API (servico centralizado em src/services/api.js).
 */
import api from '../../services/api';
import ConfirmDialog from '../../components/ConfirmDialog.vue';

export default {
  name: 'TelaUnidades',

  components: {
    ConfirmDialog
  },

  /**
   * DADOS DA TELA (o "estado" reativo do componente).
   */
  data() {
    return {
      // As unidades vindas da API.
      unidades: [],

      // Texto do campo de busca.
      search: '',

      // Mostra a barra de progresso da tabela.
      carregando: false,

      // Id da linha que esta em modo de edicao (null = nenhuma).
      editando: null,

      // Copia da linha em edicao (editar a linha original direto seria
      // perigoso: se o usuario cancelar, a mudanca ja estaria na tela).
      editado: { id: null, nome: '' },

      // Objeto da linha de inclusao. "id: null" e a marca de que esta
      // linha ainda NAO existe no banco.
      novo: { id: null, nome: null },

      // Linha que o usuario pediu para excluir (aguardando confirmacao).
      paraExcluir: null
    };
  },

  /**
   * COMPUTED PROPS: valores que o Vue recalcula sozinho quando as
   * dependencias mudam.
   */
  computed: {
    /**
     * As linhas que a tabela exibe.
     *
     * Se houver uma linha de inclusao em andamento (novo.nome !== null),
     * ela aparece PRIMEIRO, na parte de cima da tabela. Caso contrario,
     * a lista e apenas a lista real.
     */
    linhasExibidas() {
      if (this.novo.nome !== null) {
        return [this.novo].concat(this.unidades);
      }
      return this.unidades;
    },

    /**
     * As colunas da tabela.
     *
     * "value" e o campo do objeto. "sortable" permite ordenar.
     * "align" alinha o conteudo.
     */
    headers() {
      return [
        { text: 'Id', value: 'id', align: 'start', width: 90 },
        { text: 'Nome', value: 'nome' },
        { text: 'Acoes', value: 'actions', sortable: false, align: 'end', width: 110 }
      ];
    },

    /**
     * Regras de validacao do formulario.
     * Espelham as validacoes do backend (UnidadeRequest.java).
     */
    regras() {
      return {
        nome: value => !!value && String(value).trim().length > 0
          || 'Informe o nome da unidade.'
      };
    }
  },

  /**
   * CHAMADO QUANDO A TELA ABRE (gancho "mounted").
   * E aqui que carregamos a lista da API.
   */
  mounted() {
    this.carregar();
  },

  methods: {
    /**
     * Carrega (ou recarrega) a lista de unidades.
     */
    async carregar() {
      this.carregando = true;
      try {
        this.unidades = await api.get('/unidades');
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao carregar as unidades.');
      } finally {
        this.carregando = false;
      }
    },

    /**
     * Abre a linha de inclusao no topo da tabela.
     */
    comecarInclusao() {
      this.novo = { id: null, nome: '' };
    },

    /**
     * Cadastra a unidade da linha de inclusao.
     */
    async salvarNova() {
      if (!this.novo.nome || !this.novo.nome.trim()) {
        this.notificar('error', 'Informe o nome da unidade.');
        return;
      }

      try {
        await api.post('/unidades', { nome: this.novo.nome.trim() });
        this.novo = { id: null, nome: null };
        this.notificar('success', 'Unidade cadastrada com sucesso.');
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao cadastrar a unidade.');
      }
    },

    /**
     * Cancela a inclusao (a linha do topo desaparece).
     */
    cancelarInclusao() {
      this.novo = { id: null, nome: null };
    },

    /**
     * Coloca a linha em modo de edicao.
     */
    comecarEdicao(item) {
      this.editando = item.id;
      // Copia os valores: a edicao acontece numa copia, e so vai para o
      // servidor quando o usuario confirmar.
      this.editado = { id: item.id, nome: item.nome };
    },

    /**
     * Envia a edicao para a API.
     */
    async salvarEdicao() {
      if (!this.editado.nome || !this.editado.nome.trim()) {
        this.notificar('error', 'Informe o nome da unidade.');
        return;
      }

      try {
        await api.put('/unidades/' + this.editado.id, { nome: this.editado.nome.trim() });
        this.editando = null;
        this.notificar('success', 'Unidade alterada com sucesso.');
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao alterar a unidade.');
      }
    },

    /**
     * Cancela a edicao (descarta as alteracoes).
     */
    cancelarEdicao() {
      this.editando = null;
      this.editado = { id: null, nome: '' };
    },

    /**
     * Abre o dialogo de confirmacao de exclusao.
     */
    pedirExclusao(item) {
      this.paraExcluir = item;
      this.$refs.dialogoExclusao.abrir();
    },

    /**
     * Executa a exclusao (o usuario ja confirmou no dialogo).
     */
    async confirmarExclusao() {
      if (!this.paraExcluir) {
        return;
      }

      try {
        await api.delete('/unidades/' + this.paraExcluir.id);
        this.notificar('success', 'Unidade excluida com sucesso.');
        this.paraExcluir = null;
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao excluir a unidade.');
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
