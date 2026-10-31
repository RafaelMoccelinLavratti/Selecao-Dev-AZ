<template>
  <!--
     Notificacoes (mensagens de sucesso/erro).

     O Vuetify tem o "v-snackbar", que e a barrinha que aparece no rodape da
     tela. Este componente centraliza o uso dele pra todas as telas
     mostrarem as mensagens do mesmo jeito.

     Tipos: success (gravou), error (falhou), info (aviso).

     Acessibilidade: o "v-snackbar" ja' tem "role='alert'" no Vuetify 2,
     mas isso so' funciona se o texto estiver DENTRO do elemento com o
     papel - e o "v-snackbar" renderiza o conteudo num "div" interno.
     Por isso o componente mantem, alem da barrinha visivel, uma regiao
     "aria-live" propria, que sempre anuncia a mensagem - mesmo quando a
     barrinha esta' escondida (o que acontece quando a tela muda).
  -->
  <div>
    <v-snackbar
      v-model="visivel"
      :color="tipo"
      :timeout="duracao"
      bottom
      right
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <v-icon left dark aria-hidden="true">{{ icone }}</v-icon>
      {{ mensagem }}

      <template v-slot:action="{ attrs }">
        <v-btn text v-bind="attrs" @click="visivel = false">Fechar</v-btn>
      </template>
    </v-snackbar>

    <!--
      Regiao para leitores de tela.

      A WAI-ARIA define duas politicas de anuncio:

        - "polite": espera o leitor terminar a fala atual;
        - "assertive": interrompe.

      Para confirmacao de acao ("Salvo com sucesso"), o "polite" e' o
      certo: interromper uma pessoa no meio de uma leitura e' hostil.
      Para erro, o "assertive" e' melhor: a pessoa precisa saber agora.

      A regiao fica sempre no DOM (mesmo vazia) - se ela fosse criada
      so' no momento da mensagem, o leitor de tela nao a encontraria.
    -->
    <div
      class="sr-anuncio"
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      {{ anuncio }}
    </div>
  </div>
</template>

<script>
/**
 * Script do componente de notificacoes.
 *
 * As telas nao importam este componente: elas chamam o metodo que ele expoe
 * na raiz da aplicacao. Assim toda mensagem do sistema tem o mesmo formato.
 */
export default {
  name: 'AppNotificacoes',

  data() {
    return {
      // Se a barrinha esta aparecendo. Ligada ao <v-snackbar> pelo v-model:
      // quando o Vuetify fecha (por timeout ou no botao "Fechar"), ele
      // altera esta variavel.
      visivel: false,

      // Cor da barrinha. O Vuetify reconhece "success", "error", "info" e
      // "warning".
      tipo: 'success',

      // Texto exibido.
      mensagem: '',

      // Quantos ms a barrinha fica na tela antes de sumir sozinha.
      duracao: 4000,

      /**
       * A mensagem preparada para o leitor de tela.
       *
       * Repete o texto com o contexto, porque "Salvo com sucesso." sozinho
       * nao diz COM O QUE deu certo. "Unidade cadastrada com sucesso."
       * responde a pergunta que a pessoa realmente tem.
       */
      anuncio: ''
    };
  },

  /**
   * Valores calculados: o Vue recalcula sozinho quando as dependencias
   * mudam, e guarda em cache ate la.
   */
  computed: {
    /**
     * Icone conforme o tipo da mensagem. Em vez de repetir a regra dentro
     * do template, ela fica aqui num lugar so.
     *
     * Os nomes sao do Material Design Icons ("@mdi/font").
     *
     * @returns {string} o nome do icone
     */
    icone() {
      if (this.tipo === 'error') {
        return 'mdi-alert-circle';
      }
      if (this.tipo === 'info') {
        return 'mdi-information';
      }
      return 'mdi-check-circle';
    }
  },

  methods: {
    /**
     * Exibe a notificacao. E este metodo que as telas chamam.
     *
     * A ordem importa: tipo e mensagem precisam estar definidos antes de
     * "visivel" virar true, senao a barrinha aparece por um instante com o
     * conteudo anterior.
     *
     * @param {string} tipo     'success', 'error' ou 'info'
     * @param {string} mensagem texto a exibir
     */
    exibir(tipo, mensagem) {
      // O "||" protege contra chamada sem tipo (viria undefined).
      this.tipo = tipo || 'success';
      this.mensagem = mensagem;
      this.visivel = true;

      // O anuncio para o leitor de tela. Um pequeno atraso evita que a
      // leitura ocorra ao mesmo tempo da troca de texto na barrinha -
      // o que faria o leitor de tela ler a mensagem duas vezes.
      this.anuncio = '';
      this.$nextTick(() => {
        this.anuncio = mensagem;
      });
    }
  }
};
</script>

<style scoped>
/**
 * Regiao de anuncio para leitores de tela.
 *
 * A tecnica e' a "visually hidden" da WAI-ARIA: o elemento fica na tela
 * (para o leitor de tela encontra-lo e announces), mas e' cortado visualmente.
 *
 * Nao se usa "display: none" nem "visibility: hidden" - os dois tiram
 * o elemento da arvore de acessibilidade, e o leitor de tela nunca
 * anunciaria nada.
 */
.sr-anuncio {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
