<template>
  <!--
     View: empresas (views/empresa/Empresas.vue).

     Exercicio 6 - consulta parametrizada do cadastro de empresas, em uma
     grid somente leitura com cnpj, razaoSocial, telefone e email.

     Exercicio 8 (parte final) - os links para editar e excluir, sendo que
     a edicao chama a tela de cadastro pela url #/empresa/:id.

     URL da pagina.....: http://server/#/empresas
     URL dos servicos..: http://server/empresas (GET)
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
/**
 * Script da tela de empresas.
 *
 * Consulta somente leitura das empresas participantes dos leiloes, com
 * links para editar e excluir.
 *
 * O CNPJ, o telefone e o CEP aparecem mascarados na tela, mas sao gravados
 * sem pontuacao no banco: a exibicao nao e o armazenamento.
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

/**
 * Importacao dos mascaradores de exibicao.
 *
 * O "as" no import permite renomear a funcao no momento do import.
 * Fazemos isso porque os nomes sao genericos demais no arquivo de
 * origem ("mascaraCnpj" viraria um metodo chamado "mascaraCnpj" na
 * tela) e para deixar claro, aqui, que sao FORMATADORES DE EXIBICAO.
 *
 * O arquivo utils/mascaras.js concentra todos os mascaradores do
 * projeto - assim a mesma mascara aparece igual em todas as telas.
 */
import {
  mascaraCnpj as formatarCnpj,
  mascaraTelefone as formatarTelefone,
  mascaraCep as formatarCep
} from '../../utils/mascaras';

export default {
  /**
   * Nome do componente (tela).
   */
  name: 'TelaEmpresas',

  /**
   * Componentes usados nesta tela.
   */
  components: {
    ConfirmDialog
  },

  /**
   *  DADOS REATIVOS (data)
   */
  data() {
    return {
      /**
       * A lista de empresas vinda da API.
       *
       * Comeca como array VAZIO (e nao como null): o Vuetify espera um
       * array para a tabela, e um array vazio e o estado natural de
       * "ainda nao carregou" - a tabela mostra a mensagem "Nenhuma
       * empresa cadastrada" enquanto o carregamento acontece.
       */
      empresas: [],

      /**
       * Texto digitado no campo de busca.
       *
       * O v-data-table filtra automaticamente por este valor: nao
       * precisamos implementar busca na mao. A busca e "client side"
       * (roda no navegador), o que e rapido para uma lista desse
       * tamanho e nao gera trafego na rede.
       */
      search: '',

      /**
       * Controla a barra de progresso da tabela.
       *
       * Enquanto for "true", o Vuetify mostra um indicador de
       * carregamento no lugar dos dados.
       */
      carregando: false,

      /**
       * A empresa que o usuario pediu para excluir.
       *
       * Fica guardada aqui enquanto o dialogo de confirmacao esta
       * aberto. E o que permite que o metodo "confirmarExclusao" saiba
       * QUAL registro apagar (o dialogo, por si so, nao sabe).
       */
      paraExcluir: null
    };
  },

  /**
   *  VALORES CALCULADOS (computed)
   */
  computed: {
    /**
     * Colunas da tabela.
     *
     * O enunciado do exercicio 6 pede exatamente quatro campos:
     * cnpj, razaoSocial, telefone e email. Acrescentamos a coluna de
     * acoes, exigida pelo exercicio 8.
     *
     * Cada coluna tem:
     *   text  : o titulo que aparece no cabecalho;
     *   value : o nome do campo no objeto - e por ele que o Vuetify
     *           busca o valor de cada linha;
     *   width : largura fixa (opcional);
     *   align : alinhamento do conteudo ("start", "end", "center");
     *   sortable: se pode ser ordenada (o padrao e true).
     *
     * A coluna "actions" nao existe como campo no objeto - ela e um
     * "slot", um pedaco de template customizado (veja o
     * v-slot:item.actions no template).
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
   *  CICLO DE VIDA: mounted
   *
   *  "mounted" e chamado quando o componente ja foi inserido na tela.
   *  E o lugar certo para buscar dados: a tela ja existe, o usuario
   *  ve o indicador de carregamento e a chamada acontece em segundo
   *  plano.
   *
   *  (Se a busca fosse feita no "created", ela comecaria antes da tela
   *  aparecer - e o usuario veria uma tela em branco por um instante.)
   */
  mounted() {
    this.carregar();
  },

  /**
   *  METODOS (methods)
   */
  methods: {
    /**
     * Carrega (ou recarrega) a lista de empresas.
     *
     * O padrao "try / catch / finally" e o tratamento de erro padrao
     * do JavaScript:
     *
     *   try    : o codigo que pode falhar (a chamada HTTP);
     *   catch  : o que fazer se falhou (mostrar a mensagem de erro);
     *   finally: o que fazer SEMPRE, com sucesso ou erro (tirar o
     *            indicador de carregamento).
     *
     * O bloco "finally" e essencial: sem ele, se a chamada falhasse, o
     * indicador de carregamento ficaria girando para sempre na tela.
     */
    async carregar() {
      this.carregando = true;
      try {
        this.empresas = await api.get('/empresas');
      } catch (erro) {
        // O interceptor do axios (em services/api.js) ja traduziu o erro
        // do backend para o objeto { status, mensagem, campos }.
        this.notificar('error', erro.mensagem || 'Erro ao carregar as empresas.');
      } finally {
        this.carregando = false;
      }
    },

    /**
     * Mascaradores importados de utils/mascaras.js, usados na exibicao.
     *
     * Sao atribuidos como metodos do componente, para poderem ser
     * chamados no template como "mascaraCnpj(item.cnpj)".
     */
    mascaraCnpj: formatarCnpj,
    mascaraTelefone: formatarTelefone,
    mascaraCep: formatarCep,

    /**
     * Abre o dialogo de confirmacao de exclusao.
     *
     * Guardamos o registro escolhido antes de abrir o dialogo, para
     * que a confirmacao saiba o que apagar.
     */
    pedirExclusao(item) {
      this.paraExcluir = item;
      this.$refs.dialogoExclusao.abrir();
    },

    /**
     * Executa a exclusao (o usuario ja confirmou no dialogo).
     *
     * A guarda "if (!this.paraExcluir) return;" protege contra o
     * evento de confirmacao chegar sem nenhum registro selecionado.
     */
    async confirmarExclusao() {
      if (!this.paraExcluir) {
        return;
      }

      try {
        await api.delete('/empresas/' + this.paraExcluir.id);
        this.notificar('success', 'Empresa excluida com sucesso.');
        this.paraExcluir = null;
        // Recarrega a lista para a linha apagada sumir da tabela.
        await this.carregar();
      } catch (erro) {
        this.notificar('error', erro.mensagem || 'Erro ao excluir a empresa.');
      }
    },

    /**
     * Atalho para exibir a notificacao global.
     *
     * Existe para as telas nao precisarem repetir o caminho
     * "$root.$refs.notificacoes.exibir(...)" em cada chamada.
     */
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
