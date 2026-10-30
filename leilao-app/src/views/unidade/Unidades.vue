<template>
  <!--
     Unidades (views/unidade/Unidades.vue).

     Exercicio 5 - consulta e edicao do cadastro, numa grid editavel que faz
     todas as operacoes CRUD (v-data-table).

     Pagina:  #/unidades
     Servico: /unidades (GET | POST | PUT | DELETE)

     A tabela e uma so, e o que muda e o conteudo da linha, conforme o
     estado:

       1. consulta  - mostra os dados, com os icones de editar e excluir;
       2. edicao    - ao clicar no lapis, a linha vira formulario e os
                      icones viram salvar e cancelar;
       3. inclusao  - ao clicar em "Nova unidade", aparece uma linha em
                      branco no topo, pronta pra preencher.

     A lista exibida e um computed que coloca a linha de inclusao na frente
     da lista real. Nao existe uma segunda tabela na tela, e uma so que
     muda de conteudo.

     A exclusao passa por dialogo porque nao tem Ctrl+Z: a lixeira fica a
     poucos pixels do lapis, e um clique ali apagaria o cadastro sem volta.
  -->
  <div>
    <h1 class="titulo-tela">Unidades</h1>
    <p class="subtitulo-tela">
      Cadastro das unidades (locais) onde os leiloes sao realizados.
    </p>

    <v-card elevation="2">
      <!-- cabecalho: busca + botoes -->
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

        <!--
          Botao "Nova unidade". Abre a linha de inclusao no topo em vez de
          chamar a API direto: o cadastro passa pela linha, que tem os
          mesmos campos e as mesmas validacoes da edicao.
        -->
        <v-btn color="success" depressed @click="comecarInclusao">
          <v-icon left>mdi-plus</v-icon>
          Nova unidade
        </v-btn>

        <!--
          Botao "Atualizar". O ":loading" desabilita o botao enquanto a
          request esta em andamento, evitando o "cliquei duas vezes e criei
          dois registros".
        -->
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
        "item-key" e o campo que identifica cada linha (o id). Sem isso o
        Vuetify nao consegue rastrear as linhas quando a lista muda.
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
        <!-- Titulo de "Acoes" alinhado a direita. -->
        <template v-slot:header.actions>
          <span class="text-right">Acoes</span>
        </template>

        <!--
          Celula "nome": vira campo de edicao quando a linha esta em edicao
          (ou e a linha de inclusao); senao mostra o texto.
        -->
        <template v-slot:item.nome="{ item }">
          <!-- edicao de um registro existente -->
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

          <!-- inclusao (a linha nova do topo) -->
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

          <!-- consulta -->
          <span v-else>{{ item.nome }}</span>
        </template>

        <!--
          Celula de acoes. Quando a linha esta em edicao, os botoes viram
          salvar e cancelar.
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

        <!-- Mensagem quando nao hai nenhuma unidade cadastrada. -->
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

    <!-- dialogo de confirmacao de exclusao -->
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
 * Script da tela de unidades.
 *
 * Uma tabela so, e o que muda e o conteudo das linhas conforme o estado:
 * consulta, edicao ou a linha de inclusao no topo.
 *
 * A edicao acontece numa copia da linha (o objeto "editado"), e nao na
 * propria linha da lista: assim cancelar e so descartar a copia.
 */

// O axios e criado e configurado uma vez em src/services/api.js.
import api from '../../services/api';

import ConfirmDialog from '../../components/ConfirmDialog.vue';

export default {
  name: 'TelaUnidades',

  components: {
    ConfirmDialog
  },

  data() {
    return {
      // As unidades vindas da API.
      unidades: [],

      // Texto da busca. O v-data-table filtra sozinho por este valor, e
      // no navegador.
      search: '',

      // Mostra a barra de progresso da tabela.
      carregando: false,

      // Id da linha em modo de edicao (null = nenhuma). Guardamos o id e
      // nao o objeto inteiro porque o id identifica a linha de forma
      // estavel, e o template so precisa comparar valores.
      editando: null,

      // Copia da linha em edicao (ver o cabecalho deste arquivo).
      editado: { id: null, nome: '' },

      // Objeto da linha de inclusao. O "id: null" e a marca de que esta
      // linha ainda nao existe no banco.
      novo: { id: null, nome: null },

      // Linha que o usuario pediu para excluir, aguardando confirmacao.
      paraExcluir: null
    };
  },

  computed: {
    /**
     * As linhas que a tabela exibe.
     *
     * Se houver inclusao em andamento (novo.nome !== null), ela aparece
     * primeiro, no topo da tabela. Caso contrario, e so a lista real.
     *
     * O teste e em "novo.nome" e nao em "novo.id" porque o id e null tanto
     * na linha vazia quanto depois de cancelar. Ja o nome comeca como null
     * e vira string (mesmo vazia) quando o usuario comeca a digitar - e
     * isso que faz a linha aparecer e sumir na hora certa.
     */
    linhasExibidas() {
      if (this.novo.nome !== null) {
        return [this.novo].concat(this.unidades);
      }
      return this.unidades;
    },

    /**
     * Colunas da tabela. "value" e o campo do objeto, "sortable" permite
     * ordenar e "align" alinha o conteudo.
     */
    headers() {
      return [
        { text: 'Id', value: 'id', align: 'start', width: 90 },
        { text: 'Nome', value: 'nome' },
        { text: 'Acoes', value: 'actions', sortable: false, align: 'end', width: 110 }
      ];
    },

    /**
     * Regras de validacao do formulario. Espelham as do backend
     * (UnidadeRequest.java).
     *
     * Validar nos dois lados: no frontend pra dar experiencia (o erro
     * aparece na hora, sem perder o que ja foi digitado); no backend pra
     * dar garantia, porque qualquer um pode chamar a API direto, sem
     * passar pela tela.
     *
     * A regra: o nome nao pode ser vazio nem so espacos. O "trim()"
     * tira os espacos das pontas, senao o usuario digitaria "   " e o
     * campo pareceria preenchido.
     */
    regras() {
      return {
        nome: value => !!value && String(value).trim().length > 0
          || 'Informe o nome da unidade.'
      };
    }
  },

  mounted() {
    this.carregar();
  },

  methods: {
    /**
     * Carrega (ou recarrega) a lista de unidades.
     *
     * O "finally" e o que garante que o indicador de carregamento saia da
     * tela mesmo quando a chamada falha.
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
     *
     * Repare no "nome: ''" (string vazia) e nao null: e isso que faz a
     * linha aparecer e o campo receber o foco com o cursor pronto.
     */
    comecarInclusao() {
      this.novo = { id: null, nome: '' };
    },

    /**
     * Cadastra a unidade da linha de inclusao.
     *
     * O "trim()" tira os espacos das pontas antes de enviar. O backend
     * tambem faz isso (por seguranca, ja que o frontend pode ser
     * contornado), mas fazer dos dois lados evita ida e volta ao servidor
     * por causa de um espaco.
     */
    async salvarNova() {
      if (!this.novo.nome || !this.novo.nome.trim()) {
        this.notificar('error', 'Informe o nome da unidade.');
        return;
      }

      try {
        await api.post('/unidades', { nome: this.novo.nome.trim() });
        // Zera a linha de inclusao (some da tela).
        this.novo = { id: null, nome: null };
        this.notificar('success', 'Unidade cadastrada com sucesso.');
        // Recarrega pra tabela mostrar o registro novo, com o id que o
        // banco gerou.
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao cadastrar a unidade.');
      }
    },

    // Cancela a inclusao. Volta o "nome" pra null e a linha some sozinha.
    cancelarInclusao() {
      this.novo = { id: null, nome: null };
    },

    // Coloca a linha em modo de edicao, copiando os valores pra "editado".
    comecarEdicao(item) {
      this.editando = item.id;
      this.editado = { id: item.id, nome: item.nome };
    },

    /**
     * Envia a edicao para a API. O PUT leva o id na URL e os dados novos
     * no corpo, que e o padrao REST para alteracao.
     */
    async salvarEdicao() {
      if (!this.editado.nome || !this.editado.nome.trim()) {
        this.notificar('error', 'Informe o nome da unidade.');
        return;
      }

      try {
        await api.put('/unidades/' + this.editado.id, { nome: this.editado.nome.trim() });
        // Sai do modo de edicao (a linha volta a mostrar texto).
        this.editando = null;
        this.notificar('success', 'Unidade alterada com sucesso.');
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao alterar a unidade.');
      }
    },

    // Cancela a edicao. Limpar o estado descarta a copia, e a linha
    // original (que nunca foi tocada) volta a aparecer.
    cancelarEdicao() {
      this.editando = null;
      this.editado = { id: null, nome: '' };
    },

    // Guarda o registro escolhido antes de abrir o dialogo.
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
        await api.delete('/unidades/' + this.paraExcluir.id);
        this.notificar('success', 'Unidade excluida com sucesso.');
        this.paraExcluir = null;
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao excluir a unidade.');
      }
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
