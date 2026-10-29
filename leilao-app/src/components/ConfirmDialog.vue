<template>
  <!--
    ==========================================================================
     COMPONENTE: CONFIRMACAO DE EXCLUSAO
    ==========================================================================
     Toda exclusao passa por uma confirmacao. Dois motivos:
       1) UX: exclusao nao tem "Ctrl+Z". Um clique acidental em um icone
          pequeno (o botao "lixeira" ao lado de cada linha) apagaria um
          cadastro sem chance de recuperar.
       2) Seguranca: a acao destrutiva fica explicita e registrada. O
          usuario precisa olhar para o que esta fazendo.

     O componente e "generico": recebe o texto e emite os eventos
     'confirmar' e 'cancelar'. Quem chama decide o que fazer.
    ==========================================================================
  -->
  <v-dialog v-model="visivel" max-width="420">
    <v-card>
      <v-card-title class="text-h6">
        <v-icon left color="error">mdi-alert</v-icon>
        {{ titulo }}
      </v-card-title>

      <v-card-text>
        {{ mensagem }}
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text @click="cancelar">Cancelar</v-btn>
        <v-btn color="error" depressed @click="confirmar">Sim, excluir</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  name: 'ConfirmDialog',

  props: {
    // Titulo do dialogo.
    titulo: {
      type: String,
      default: 'Confirmar exclusao'
    },
    // Texto que explica o que vai acontecer.
    mensagem: {
      type: String,
      default: 'Deseja realmente excluir este registro?'
    }
  },

  data() {
    return {
      visivel: false
    };
  },

  methods: {
    /**
     * Abre o dialogo. Chamado pela tela que quer excluir.
     */
    abrir() {
      this.visivel = true;
    },

    /**
     * Usuario confirmou: fecha e avisa quem chamou.
     */
    confirmar() {
      this.visivel = false;
      this.$emit('confirmar');
    },

    /**
     * Usuario cancelou: fecha sem fazer nada.
     */
    cancelar() {
      this.visivel = false;
      this.$emit('cancelar');
    }
  }
};
</script>
