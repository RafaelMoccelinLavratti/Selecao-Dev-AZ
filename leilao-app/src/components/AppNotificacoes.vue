<template>
  <!--
     COMPONENTE: NOTIFICACOES (mensagens de sucesso/erro)
     O Vuetify tem o "v-snackbar", que e aquela barrinha que aparece no
     rodape da tela. Este componente centraliza o uso dele para que
     todas as telas mostrem as mensagens do mesmo jeito.

     Tipos:
       success -> gravacao OK
       error   -> falha na operacao
       info    -> aviso neutro
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
 * As telas nao importam este componente: elas chamam o metodo que o
 * componente expoe na raiz da aplicacao. Um unico componente garante que
 * toda mensagem do sistema tenha o mesmo formato.
 */
export default {
  /**
   * Nome do componente.
   */
  name: 'AppNotificacoes',

  /**
   *  DADOS REATIVOS (data)
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
   *  VALORES CALCULADOS (computed)
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
   *  METODOS (methods)
   *
   *  Sao as acoes que o componente expoe para fora. As telas chamam
   *  estes metodos; o componente nunca chama os delas.
   */
  methods: {
    /**
     * Exibe a notificacao. E este metodo que as telas chamam.
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
