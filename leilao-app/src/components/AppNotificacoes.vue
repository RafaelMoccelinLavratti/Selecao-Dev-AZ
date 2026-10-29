<template>
  <!--
    ==========================================================================
     COMPONENTE: NOTIFICACOES (mensagens de sucesso/erro)
    ==========================================================================
     O Vuetify tem o "v-snackbar", que e aquela barrinha que aparece no
     rodape da tela. Este componente centraliza o uso dele para que
     todas as telas mostrem as mensagens do mesmo jeito.

     TIPOS:
       success -> gravacao OK
       error   -> falha na operacao
       info    -> aviso neutro
    ==========================================================================
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
export default {
  name: 'AppNotificacoes',

  data() {
    return {
      visivel: false,
      tipo: 'success',
      mensagem: '',
      duracao: 4000
    };
  },

  computed: {
    /**
     * Icone conforme o tipo da mensagem.
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
     * Exibe a notificação. E este metodo que as telas chamam.
     *
     * @param {string} tipo     'success', 'error' ou 'info'
     * @param {string} mensagem texto a exibir
     */
    exibir(tipo, mensagem) {
      this.tipo = tipo || 'success';
      this.mensagem = mensagem;
      this.visivel = true;
    }
  }
};
</script>
