/*
 * Configuracao do ESLint (.eslintrc.js).
 *
 * Este arquivo fica aqui (e nao dentro do package.json) porque permite
 * COMENTAR as configuracoes - o que e essencial para explicar as decisoes.
 *
 * O ESLint analisa o codigo automaticamente e aponta problemas ANTES de a
 * pessoa rodar a aplicacao: variavel nao usada, erro de digitacao em um
 * nome de metodo, template invalido, etc.
 */
module.exports = {
  root: true,

  env: {
    node: true,
    browser: true
  },

  extends: [
    'plugin:vue/essential', // regras essenciais do Vue 2
    'eslint:recommended'     // regras gerais do JavaScript
  ],

  parserOptions: {
    // O babel-eslint analisa o bloco <script> (e nao o <template>,
    // que e analisado pelo vue-eslint-parser, um parser especifico).
    parser: 'babel-eslint'
  },

  rules: {
    /**
     * Por que desligar a regra "vue/valid-v-slot"?
     *
     * O Vuetify 2 (documentacao oficial) usa este padrao para os slots da
     * tabela:
     *
     *     <template v-slot:item.nome="{ item }">
     *
     * Nesse padrao, "item.nome" e o NOME do slot (o Vuetify procura
     * exatamente por "item." + nomeDaColuna). E o que funciona em runtime
     * com o Vue 2.6.
     *
     * Porem o analisador estatico do ESLint interpreta o ponto como um
     * "modificador" de diretiva (semantica do Vue 3) e reclama:
     *
     *     'v-slot' directive doesn't support any modifier
     *
     * E um falso positivo: o codigo funciona (e e o exemplo oficial do
     * Vuetify). Desligar a regra - em vez de trocar a sintaxe, que
     * quebraria a tabela - e a decisao correta. As outras 2 regras da
     * familia (vue/no-deprecated-slot-attribute e
     * vue/no-deprecated-slot-scope) continuam LIGADAS, o que garante que a
     * sintaxe antiga de slots nao seja usada.
     */
    'vue/valid-v-slot': 'off',

    /**
     * Nomes de componentes com mais de uma palavra evitam conflito
     * com o HTML. As views deste projeto seguem o padrao "TelaX".
     */
    'vue/multi-word-component-names': 'warn',

    /**
     * Os "console.log" em ambiente de producao poluem o console do
     * navegador e podem vazar informacao. Este projeto usa o componente
     * de notificacoes no lugar deles.
     */
    'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off'
  }
};
