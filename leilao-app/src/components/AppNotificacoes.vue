<template>
  <!--
     Notificacoes (mensagens de sucesso/erro).

     O Vuetify tem o "v-snackbar", que e a barrinha que aparece no rodape da
     tela. Este componente centraliza o uso dele pra todas as telas
     mostrarem as mensagens do mesmo jeito.

     Tipos: success (gravou), error (falhou), info (aviso).
  -->
  <v-snackbar
    v-model="visivel"
    :color="tipo"
    :timeout="duracao"
    bottom
    right
  >
    <v-icon left dark>{{ icone }}</v-icon>
    {{ mensagem }}

    <template v-slot:action="{ attrs }">
      <v-btn text v-bind="attrs" @click="visivel = false">Fechar</v-btn>
    </template>
  </v-snackbar>
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
      duracao: 4000
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
    }
  }
};
</script>
