<template>
  <!--
     Confirmacao de exclusao.

     Toda exclusao passa por aqui por dois motivos:
       1) UX: exclusao nao tem Ctrl+Z. Um clique acidental na lixeira
          (que fica do lado do lapis) apagaria um cadastro sem volta;
       2) Seguranca: a acao destrutiva fica explicita. O usuario precisa
          olhar pra o que esta fazendo.

     O componente e generico: recebe o texto e emite 'confirmar' e
     'cancelar'. Quem chama decide o que fazer.
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
/**
 * Script do componente de confirmacao de exclusao.
 *
 * Padrao "props + eventos": o componente nao sabe o que esta sendo
 * excluido, recebe o texto e emite a confirmacao.
 *
 * Exclusao nao tem Ctrl+Z e a lixeira fica a poucos pixels do lapis de
 * editar, por isso toda exclusao passa por aqui.
 */
export default {
  name: 'ConfirmDialog',

  /**
   * Dados que o componente pai envia.
   *
   * Declarar o type evita erro silencioso: se a tela passar um numero onde
   * se espera texto, o Vue avisa no console em desenvolvimento.
   */
  props: {
    // Titulo do dialogo (em cima, ao lado do icone de alerta).
    titulo: {
      type: String,
      default: 'Confirmar exclusao'
    },

    // Texto do que vai acontecer. A tela monta com o nome do registro, pra
    // o usuario ver exatamente o que esta prestes a apagar, e nao um
    // "tem certeza?" generico.
    mensagem: {
      type: String,
      default: 'Deseja realmente excluir este registro?'
    }
  },

  data() {
    return {
      // Comeca fechado: o dialogo so abre quando clica no botao de excluir.
      visivel: false
    };
  },

  methods: {
    // Abre o dialogo. Quem chama usa
    // "this.$refs.dialogoExclusao.abrir()".
    abrir() {
      this.visivel = true;
    },

    // Confirmou: fecha e avisa quem chamou com o evento "confirmar".
    // A tela e quem decide o que fazer (chamar o DELETE, mostrar a
    // notificacao, recarregar a lista).
    confirmar() {
      this.visivel = false;
      this.$emit('confirmar');
    },

    // Cancelou: fecha sem fazer nada. O evento "cancelar" existe so pra
    // deixar o fluxo explicito.
    cancelar() {
      this.visivel = false;
      this.$emit('cancelar');
    }
  }
};
</script>
