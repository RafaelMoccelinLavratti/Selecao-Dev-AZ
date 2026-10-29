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
/**
 * ============================================================================
 *  SCRIPT DO COMPONENTE: NOTIFICACOES
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  COMO UMA TELA USA ESTE COMPONENTE
 *  ------------------------------------------------------
 *  As telas nao importam este componente diretamente. Elas chamam o
 *  componente de notificacoes que fica na RAIZ da aplicacao (dentro do
 *  App.vue), atraves do metodo auxiliar "notificar".
 *
 *  Em cada tela existe este trecho:
 *
 *      notificar(tipo, mensagem) {
 *        if (this.$root.$refs.notificacoes) {
 *          this.$root.$refs.notificacoes.exibir(tipo, mensagem);
 *        }
 *      }
 *
 *  O caminho "$root.$refs.notificacoes" significa:
 *      $root        -> o componente raiz da aplicacao (o App.vue);
 *      $refs        -> os componentes filhos registrados com "ref";
 *      .notificacoes-> o nome dado no template: <app-notificacoes ref="notificacoes" />.
 *
 *  ------------------------------------------------------
 *  POR QUE UM COMPONENTE UNICO PARA AS NOTIFICACOES?
 *  ------------------------------------------------------
 *  Tres motivos:
 *    1) CONSISTENCIA: toda mensagem do sistema tem o mesmo formato, a
 *       mesma posicao e o mesmo tempo de duracao. O usuario aprende
 *       a ler em uma tela e aplica o mesmo nas outras.
 *    2) UM SO ELEMENTO NA TELA: o <v-snackbar> fica no App.vue. Se cada
 *       tela tivesse o seu, existiriam varios snackbars na pilha de
 *       componentes, e o comportamento (qual aparece por cima) seria
 *       imprevisivel.
 *    3) SEPARACAO DE RESPONSABILIDADES: quem mostra a mensagem nao e
 *       quem decide o que aconteceu. A tela decide ("deu erro"), o
 *       componente decide COMO mostrar.
 * ============================================================================
 */
export default {
  /**
   * Nome do componente.
   */
  name: 'AppNotificacoes',

  /**
   * ------------------------------------------------------------------
   *  DADOS REATIVOS (data)
   * ------------------------------------------------------------------
   *
   *  - "visivel"  : controla se a barrinha aparece. E ligado ao
   *                 <v-snackbar> pelo "v-model" - quando o Vuetify
   *                 fecha a barrinha (por timeout ou pelo botao
   *                 "Fechar"), ele altera esta variavel.
   *  - "tipo"     : a cor da barrinha. O Vuetify reconhece os nomes
   *                 "success", "error", "info" e "warning".
   *  - "mensagem" : o texto exibido.
   *  - "duracao"  : quantos milissegundos a barrinha fica na tela
   *                 antes de sumir sozinha. 4000 = 4 segundos.
   */
  data() {
    return {
      visivel: false,
      tipo: 'success',
      mensagem: '',
      duracao: 4000
    };
  },

  /**
   * ------------------------------------------------------------------
   *  VALORES CALCULADOS (computed)
   * ------------------------------------------------------------------
   *
   *  Um "computed" e um valor que o Vue RECALCULA SOZINHO quando as
   *  dependencias mudam - e sempre devolve o resultado em cache ate
   *  que algo mude. E diferente de um metodo, que recalcularia a cada
   *  renderizacao.
   *
   *  Aqui, o icone do tipo da mensagem. Em vez de repetir a logica de
   *  "se for erro, desenha o alerta; se for info, desenha o i; senao,
   *  desenha o visto" dentro do template, a regra fica em um unico
   *  lugar, testavel e legivel.
   */
  computed: {
    /**
     * Icone conforme o tipo da mensagem.
     *
     * Os nomes dos icones sao do Material Design Icons (biblioteca
     * instalada como "@mdi/font").
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

  /**
   * ------------------------------------------------------------------
   *  METODOS (methods)
   * ------------------------------------------------------------------
   *
   *  Sao as acoes que o componente expoe para fora. As telas chamam
   *  estes metodos; o componente nunca chama os delas.
   */
  methods: {
    /**
     * Exibe a notificação. E este metodo que as telas chamam.
     *
     * A ordem das atribuicoes importa: o "tipo" e a "mensagem" precisam
     * estar definidos ANTES de "visivel" virar true. Se invertessemos,
     * a barrinha apareceria por um instante com o conteudo anterior.
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
