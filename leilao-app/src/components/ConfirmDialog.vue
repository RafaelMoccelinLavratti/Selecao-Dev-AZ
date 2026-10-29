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
/**
 * ============================================================================
 *  SCRIPT DO COMPONENTE: CONFIRMACAO DE EXCLUSAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O PADRAO "PROPS + EVENTOS" (props down, events up)
 *  ------------------------------------------------------
 *  Este componente e "generico": ele nao sabe O QUE esta sendo excluido.
 *  Ele sabe apenas que precisa perguntar "tem certeza?" e avisar quem
 *  chamou a resposta.
 *
 *  Isso segue o padrao classico do Vue, chamado de "props down, events up"
 *  (propriedades descem, eventos sobem):
 *
 *      QUEM CHAMA (a tela)                    ESTE COMPONENTE
 *      -------------------------               ------------------
 *      <confirm-dialog                        recebe "titulo" e "mensagem"
 *        titulo="..."                          como "props" (dados que
 *        :mensagem="..."                       DESCEM para o filho)
 *        @confirmar="confirmarExclusao"        emite o evento "confirmar"
 *        @cancelar="..." />                    (evento que SOBE para o pai)
 *
 *  Quem controla a EXECUCAO e a tela, nao o dialogo. O dialogo apenas
 *  pergunta e responde. Isso torna o componente reutilizavel: serve
 *  para excluir unidade, empresa, lote, leilao...
 *
 *  ------------------------------------------------------
 *  POR QUE USAR "$refs"?
 *  ------------------------------------------------------
 *  O dialogo precisa ser ABERTO de fora ("this.$refs.dialogoExclusao.abrir()").
 *  Para isso, a tela registra o componente com "ref" no template:
 *
 *      <confirm-dialog ref="dialogoExclusao" ... />
 *
 *  E o Vue guarda a instancia do componente em "this.$refs.dialogoExclusao",
 *  permitindo chamar os metodos publicos dele.
 * ============================================================================
 */
export default {
  /**
   * Nome do componente.
   */
  name: 'ConfirmDialog',

  /**
   * ------------------------------------------------------------------
   *  PROPS: os dados que o componente PAI envia para este.
   * ------------------------------------------------------------------
   *
   *  Cada prop pode declarar o "type" (tipo esperado) e o "default"
   *  (valor usado quando o pai nao informa). Declarar o tipo evita erros
   *  silenciosos: se a tela passar um numero onde se espera um texto, o
   *  Vue avisa no console durante o desenvolvimento.
   */
  props: {
    /**
     * Titulo do dialogo (aparece em cima, ao lado do icone de alerta).
     */
    titulo: {
      type: String,
      default: 'Confirmar exclusao'
    },

    /**
     * Texto que explica o que vai acontecer.
     *
     * A tela monta esse texto com o nome do registro, para que o usuario
     * veja EXATAMENTE o que esta prestes a apagar - e nao apenas um
     * "tem certeza?" generico.
     */
    mensagem: {
      type: String,
      default: 'Deseja realmente excluir este registro?'
    }
  },

  /**
   * ------------------------------------------------------------------
   *  DADOS REATIVOS (data)
   * ------------------------------------------------------------------
   *
   *  "visivel" controla a abertura do <v-dialog>. Comeca em false
   *  (dialogo fechado) porque o dialogo so aparece quando o usuario
   *  clica no botao de excluir.
   */
  data() {
    return {
      visivel: false
    };
  },

  /**
   * ------------------------------------------------------------------
   *  METODOS (methods)
   * ------------------------------------------------------------------
   */
  methods: {
    /**
     * Abre o dialogo. Chamado pela tela que quer excluir.
     *
     * E um metodo PUBLICO do componente: quem chama usa
     * "this.$refs.dialogoExclusao.abrir()".
     */
    abrir() {
      this.visivel = true;
    },

    /**
     * Usuario confirmou: fecha o dialogo e avisa quem chamou.
     *
     * O "$emit" dispara o evento "confirmar", que no template da tela
     * esta ligado a "@confirmar". A tela e quem decide o que fazer
     * (chamar o DELETE na API, mostrar a notificacao, recarregar a
     * lista).
     */
    confirmar() {
      this.visivel = false;
      this.$emit('confirmar');
    },

    /**
     * Usuario cancelou: fecha sem fazer nada.
     *
     * Emite o evento "cancelar" para quem chamou saber que o usuario
     * desistiu (a tela normalmente nao precisa fazer nada nesse caso -
     * mas o evento existe para deixar o fluxo explicito).
     */
    cancelar() {
      this.visivel = false;
      this.$emit('cancelar');
    }
  }
};
</script>
