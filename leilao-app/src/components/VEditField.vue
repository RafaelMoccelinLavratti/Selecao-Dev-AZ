<template>
  <!--
    Campo de edicao dentro de uma linha de tabela (components/VEditField.vue).

    A ideia e' ter um unico campo que sabe se salvar e se cancelar, sem
    repetir a mesma logica em cada tela que edita inline.

    O componente faz tres coisas:

      1. valida o campo (as mesmas regras que a tela passa);
      2. emite "save" quando o valor passa nas regras;
      3. emite "cancel" quando a pessoa desiste.

    Por que a validacao mora aqui, e nao na tela?

    Porque "salvar" e' uma acao que so faz sentido com um valor valido.
    Se quem emite "save" (a tela) precisasse validar antes, cada tela
    repetiria a mesma checagem - e uma delas esquecendo deixaria o
    usuario enviar dado invalido para a API. A API recusa, nao quebra,
    mas a mensagem chega como erro de servidor em vez de erro de campo.

    A excecao e' o "cancel": ele sempre emite, mesmo com valor invalido.
    Descartar um texto invalido e' sempre legitimo.
  -->
  <v-text-field
    ref="campo"
    v-model="valorLocal"
    :label="label"
    :rules="rules"
    :single-line="singleLine"
    :dense="dense"
    :autofocus="autofocus"
    :append-icon="icone"
    :disabled="salvando"
    hide-details="auto"
    class="campo-edicao"
    @keyup.enter="salvar"
    @keyup.esc="cancelar"
    @blur="aoPerderFoco"
  >
    <template v-slot:append>
      <!--
        Botoes de confirmar e cancelar. Eles ficam no "append" para nao
        ocupar espaco na linha da tabela, e nao num "v-menu" (que
        custaria dois cliques).
      -->
      <v-btn
        icon
        small
        color="success"
        :loading="salvando"
        title="Salvar"
        @click.stop="salvar"
      >
        <v-icon small>mdi-check</v-icon>
      </v-btn>

      <v-btn
        icon
        small
        title="Cancelar"
        @click.stop="cancelar"
      >
        <v-icon small>mdi-close</v-icon>
      </v-btn>
    </template>
  </v-text-field>
</template>

<script>
/**
 * Script do campo de edicao inline.
 *
 * O componente e' "controlado": o valor vem de fora (o "v-model" da
 * tela) e o que ele emite sao duas intencoes - salvar ou cancelar.
 * Ele nunca grava nada sozinho. Isso mantem a tela dona do estado, e o
 * componente dono do comportamento do campo.
 *
 * Como a tela usa (o exemplo fica aqui, e nao no template, porque o
 * compilador de template do Vue avalia os bindings mesmo dentro de
 * comentarios HTML - e um exemplo com ":rules" ali virava um aviso de
 * propriedade inexistente):
 *
 *     <v-edit-field
 *       v-model="editado.nome"
 *       :rules="[regras.nome]"
 *       label="Nome"
 *       @save="salvarEdicao"
 *       @cancel="cancelarEdicao"
 *     />
 */
export default {
  name: 'VEditField',

  props: {
    /**
     * O valor do campo. O "v-model" da tela.
     */
    value: {
      type: String,
      default: ''
    },

    /**
     * Rotulo do campo.
     */
    label: {
      type: String,
      default: ''
    },

    /**
     * As regras de validacao (o mesmo formato do "v-text-field").
     */
    rules: {
      type: Array,
      default: () => []
    },

    /**
     * Campo de uma linha so.
     */
    singleLine: {
      type: Boolean,
      default: false
    },

    /**
     * Versao compacta (menos altura).
     */
    dense: {
      type: Boolean,
      default: false
    },

    /**
     * Coloca o cursor no campo ao abrir.
     */
    autofocus: {
      type: Boolean,
      default: false
    },

    /**
     * Icone no canto do campo.
     */
    icone: {
      type: String,
      default: ''
    }
  },

  data() {
    return {
      /**
       * Uma copia local do valor.
       *
       * A copia e' necessaria porque o "v-model" da tela so muda quando
       * o usuario confirma (no "save"). Enquanto isso, o texto digitado
       * vive aqui - e no "cancel", o valor local e' descartado e a tela
       * volta ao valor original.
       */
      valorLocal: this.value,

      /**
       * A gravacao esta' em andamento?
       *
       * Enquanto e' true, os botoes ficam desabilitados: evita o
       * duplo clique, que criaria duas requisicoes para o mesmo
       * registro.
       */
      salvando: false
    };
  },

  watch: {
    /**
     * Quando o valor de fora muda (a tela trocou o registro em edicao, ou
     * cancelou), a copia local acompanha.
     */
    value: function (novo) {
      this.valorLocal = novo;
    }
  },

  methods: {
    /**
     * Salva, se o valor passar nas regras.
     *
     * A validacao e' feita pelo "v-form" interno do "v-text-field", que
     * expoe o metodo "validate()". Ele devolve um array com os erros -
     * vazio quando tudo passou.
     */
    salvar() {
      if (this.salvando) {
        return;
      }

      const campo = this.$refs.campo;
      const erros = campo && campo.validate ? campo.validate() : [];

      if (erros && erros.length > 0) {
        // Com erro, o campo fica marcado e o foco volta para ele. O
        // usuario ve onde esta o problema, sem perder o que digitou.
        return;
      }

      this.salvando = true;

      // A tela e' dona do estado: o componente so avisa a intencao.
      this.$emit('input', this.valorLocal);
      this.$emit('save', this.valorLocal);
    },

    /**
     * Descarta a edicao.
     *
     * O valor local volta ao valor de origem, e o "cancel" e' emitido.
     * A tela e' quem decide o que fazer com isso (normalmente, sair do
     * modo de edicao).
     */
    cancelar() {
      this.valorLocal = this.value;
      this.$emit('cancel');
    },

    /**
     * O que fazer quando o campo perde o foco.
     *
     * Nao salva: perder o foco acontece por clique fora, por Tab, por
     * troca de tela - nenhuma dessas e' uma intencao explicita de
     * salvar. Salvar ao perder o foco faria a pessoa gravar sem querer.
     *
     * O que faz e restaurar o valor, para a edicao nao ficar com um
     * texto solto caso a pessoa desista e volte depois.
     */
    aoPerderFoco() {
      if (this.salvando) {
        return;
      }
      this.valorLocal = this.value;
    }
  }
};
</script>

<style scoped>
/*
 * O campo precisa ocupar a celula inteira, e o "v-text-field" tem uma
 * margem padrao que deixa a linha desalinhada.
 */
.campo-edicao >>> .v-input__slot {
  margin-bottom: 0;
}
</style>
