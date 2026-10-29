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

     ------------------------------------------------------
     POR QUE OS BOTOES DE EXCLUIR USAM UM DIALOGO?
     ------------------------------------------------------
     Exclusao nao tem "Ctrl+Z". Um clique acidental no icone de lixeira
     - que fica a poucos pixels do lapis de editar - apagaria um
     cadastro sem chance de recuperar. Por isso, o componente
     ConfirmDialog e usado em TODA exclusao do sistema.
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

        <!--
          BOTAO "NOVA UNIDADE".
          Abre a linha de inclusao no topo da tabela (nao chama a API
          direto: o cadastro passa pela linha, que tem os mesmos
          campos e as mesmas validacoes da edicao).
        -->
        <v-btn color="success" depressed @click="comecarInclusao">
          <v-icon left>mdi-plus</v-icon>
          Nova unidade
        </v-btn>

        <!--
          BOTAO "ATUALIZAR".
          O ":loading" liga o indicador de carregamento do proprio
          botao ao estado "carregando" - o clique fica desabilitado
          enquanto a request esta em andamento, evitando o erro
          clasico de "cliquei duas vezes e criei dois registros".
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
        TABELA.
        "headers"  = as colunas (definidas no metodo, no script).
        "items"    = os dados (a lista computada "linhasExibidas").
        "search"   = o texto do campo de busca (filtra automaticamente).
        "loading"  = mostra a barra de progresso enquanto carrega.
        "item-key" = o campo que identifica univocamente cada linha
                     (o id). Sem isso, o Vuetify nao consegue rastrear
                     as linhas quando a lista muda.
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
 * ============================================================================
 *  SCRIPT DA VIEW: UNIDADES
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE ESTA TELA FAZ
 *  ------------------------------------------------------
 *  Cadastro completo (CRUD) das unidades - os locais fisicos onde os
 *  leiloes sao realizados. E a tela do EXERCICIO 5.
 *
 *  ---------------------------------------------------------
 *  A IDEIA CENTRAL: UMA TABELA, TRES ESTADOS
 *  ---------------------------------------------------------
 *  A tela tem uma unica tabela. O que muda e o CONTEUDO das linhas,
 *  conforme o estado:
 *
 *    1) CONSULTA  - mostra os dados. No canto direito, os botoes
 *                   "editar" (lapis) e "excluir" (lixeira).
 *
 *    2) EDICAO    - ao clicar no lapis, a linha vira um FORMULARIO
 *                   (o texto vira um campo editavel) e os botoes viram
 *                   "salvar" (check) e "cancelar" (X).
 *
 *    3) INCLUSAO - ao clicar em "Nova unidade", uma linha em branco
 *                   aparece NO TOPO da tabela, pronta para preencher.
 *                   Ao confirmar, ela vira um registro novo na API.
 *
 *  Como nao existe uma segunda tabela na tela, o usuario nunca fica
 *  "desorientado" sobre onde esta preenchendo alguma coisa: o que
 *  esta sendo editado esta sempre visivel, na propria linha.
 *
 *  ---------------------------------------------------------
 *  POR QUE A EDICAO ACONTECE NUMA COPIA?
 *  ---------------------------------------------------------
 *  Repare no objeto "editado": ele e uma COPIA da linha original, e
 *  nao a propria linha da lista.
 *
 *  Se editassemos o objeto original, o texto mudaria na tela
 *  imediatamente, ao digitar. Se o usuario clicasse em "cancelar", o
 *  valor original ja teria sumido - e nao teriamos como voltar atras
 *  sem recarregar a pagina inteira.
 *
 *  Com a copia, o cancelamento e simplesmente "esquecer a copia": o
 *  estado original nunca foi tocado. Esse padrao (editar uma copia,
 *  confirmar ou descartar) e o mesmo de qualquer formulario.
 * ============================================================================
 */

/**
 * Importacao do servico centralizado de comunicacao com a API.
 *
 * O axios e criado e configurado UMA vez em src/services/api.js. As
 * telas nunca chamam o axios direto - elas usam este "api", que ja
 * tem o endereco base, o timeout e o tratamento de erro padronizado.
 */
import api from '../../services/api';

/**
 * Importacao do componente de confirmacao de exclusao.
 */
import ConfirmDialog from '../../components/ConfirmDialog.vue';

export default {
  /**
   * Nome do componente (tela).
   */
  name: 'TelaUnidades',

  /**
   * Componentes usados nesta tela.
   */
  components: {
    ConfirmDialog
  },

  /**
   * ------------------------------------------------------------------
   *  DADOS DA TELA (o "estado" reativo do componente).
   * ------------------------------------------------------------------
   */
  data() {
    return {
      /**
       * As unidades vindas da API.
       */
      unidades: [],

      /**
       * Texto do campo de busca.
       *
       * O v-data-table filtra automaticamente por este valor (client
       * side): nao precisamos implementar busca na mao.
       */
      search: '',

      /**
       * Mostra a barra de progresso da tabela.
       */
      carregando: false,

      /**
       * Id da linha que esta em modo de edicao (null = nenhuma).
       *
       * Guarda o ID (e nao o objeto inteiro) porque o ID e o que
       * identifica a linha de forma univoca e estavel. Com o ID, o
       * template consegue perguntar "esta linha esta em edicao?" apenas
       * comparando valores.
       */
      editando: null,

      /**
       * Copia da linha em edicao.
       *
       * Explicado no cabecalho: a edicao acontece numa copia, e so vai
       * para o servidor quando o usuario confirmar.
       */
      editado: { id: null, nome: '' },

      /**
       * Objeto da linha de inclusao.
       *
       * O "id: null" e a MARCA de que esta linha ainda NAO existe no
       * banco. E assim que o template sabe que aquele campo de texto
       * deve ser preenchido pelo usuario (e nao mostrar um valor
       * existente).
       */
      novo: { id: null, nome: null },

      /**
       * Linha que o usuario pediu para excluir (aguardando confirmacao).
       */
      paraExcluir: null
    };
  },

  /**
   * ------------------------------------------------------------------
   *  COMPUTED PROPS: valores que o Vue recalcula sozinho quando as
   *  dependencias mudam.
   * ------------------------------------------------------------------
   */
  computed: {
    /**
     * As linhas que a tabela exibe.
     *
     * Se houver uma linha de inclusao em andamento (novo.nome !== null),
     * ela aparece PRIMEIRO, na parte de cima da tabela. Caso contrario,
     * a lista e apenas a lista real.
     *
     * Por que "novo.nome !== null" e nao "novo.id !== null"?
     * Porque o "id" e null tanto na linha vazia (ainda nao preenchida)
     * quanto quando a inclusao foi cancelada. Ja o "nome" comeca como
     * null e vira uma string (mesmo vazia) quando o usuario comeca a
     * digitar. Esse detalhe e o que faz a linha aparecer e sumir no
     * momento certo.
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
     *
     * Espelham as validacoes do backend (UnidadeRequest.java).
     *
     * POR QUE VALIDAR NOS DOIS LADOS?
     *  - No FRONTEND, para dar EXPERIENCIA: o usuario ve o erro na
     *    hora, sem perder o que ja digitou e sem gastar uma ida ao
     *    servidor.
     *  - No BACKEND, para dar GARANTIA: e o unico lugar que pode
     *    garantir a regra, porque qualquer um pode chamar a API direto
     *    (curl, Postman, outro sistema), sem passar pela tela.
     *
     * O frontend e convenience; o backend e a garantia.
     *
     * A regra em si: o nome nao pode ser vazio nem apenas espacos.
     * O "String(value).trim()" remove os espacos das pontas - senao
     * o usuario digitaria "   " (tres espacos) e o campo pareceria
     * preenchido.
     */
    regras() {
      return {
        nome: value => !!value && String(value).trim().length > 0
          || 'Informe o nome da unidade.'
      };
    }
  },

  /**
   * ------------------------------------------------------------------
   *  CICLO DE VIDA: mounted
   * ------------------------------------------------------------------
   *
   *  Carregamos a lista quando a tela entra no ar (e nao no "created"),
   *  para que o usuario ja veja o indicador de carregamento.
   */
  mounted() {
    this.carregar();
  },

  /**
   * ------------------------------------------------------------------
   *  METODOS (methods)
   * ------------------------------------------------------------------
   */
  methods: {
    /**
     * Carrega (ou recarrega) a lista de unidades.
     *
     * O padrao "try / catch / finally":
     *   try    : a chamada HTTP (pode falhar);
     *   catch  : mostra a mensagem de erro;
     *   finally: SEMPRE desliga o indicador de carregamento (sem isso,
     *            ele ficaria girando para sempre em caso de erro).
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
     * Repare que definimos "nome: ''" (string vazia) e nao "null".
     * Isso e o que faz a linha APARECER (o computed "linhasExibidas"
     * checa "novo.nome !== null") e o que faz o campo de texto receber
     * o foco com o cursor pronto para digitar.
     */
    comecarInclusao() {
      this.novo = { id: null, nome: '' };
    },

    /**
     * Cadastra a unidade da linha de inclusao.
     *
     * O "trim()" remove os espacos das pontas ANTES de enviar. E o
     * backend tambem faz isso (por seguranca - o frontend pode ser
     * contornado), mas fazer dos dois lados evita ida e volta ate o
     * servidor por causa de um espaco.
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
        // Recarrega para a tabela mostrar o registro novo, com o id
        // que o banco gerou.
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao cadastrar a unidade.');
      }
    },

    /**
     * Cancela a inclusao (a linha do topo desaparece).
     *
     * Basta voltar o "nome" para null - a linha some sozinha, porque
     * o computed "linhasExibidas" deixa de inclui-la.
     */
    cancelarInclusao() {
      this.novo = { id: null, nome: null };
    },

    /**
     * Coloca a linha em modo de edicao.
     *
     * Copiamos os valores para o objeto "editado": a edicao acontece
     * numa copia, e so vai para o servidor quando o usuario confirmar.
     */
    comecarEdicao(item) {
      this.editando = item.id;
      this.editado = { id: item.id, nome: item.nome };
    },

    /**
     * Envia a edicao para a API.
     *
     * O PUT recebe o id na URL e os dados novos no corpo - esse e o
     * padrao REST para alteracao.
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

    /**
     * Cancela a edicao (descarta as alteracoes).
     *
     * Basta limpar o estado de edicao: a copia e descartada e a linha
     * original (que nunca foi tocada) volta a aparecer.
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
     *
     * A guarda inicial protege contra o evento de confirmacao chegar
     * sem nenhum registro selecionado.
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
