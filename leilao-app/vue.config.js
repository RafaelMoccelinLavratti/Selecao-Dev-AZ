/**
 * Configuracao do Vue CLI (vue.config.js).
 *
 * Decisao 1: Vue CLI 5 em vez do 4 (que vinha no starter).
 *
 * O starter usava @vue/cli-service 4, que empacota o webpack 4. O webpack 4
 * calcula o hash dos arquivos usando o algoritmo MD4 do OpenSSL - e o
 * OpenSSL 3 (padrao em Linux e a partir do Node 17) REMOVEU esse algoritmo.
 * Ao rodar "npm run serve" em uma maquina atual, o build quebra com:
 *
 *     error:0308010C:digital envelope routines::unsupported
 *     (ERR_OSSL_EVP_UNSUPPORTED)
 *
 * A solucao mais comum seria "--openssl-legacy-provider", mas ela so existe
 * a partir do Node 17 e descontinua no Node 22+. Subir para o Vue CLI 5
 * (webpack 5) resolve de vez: o hash passa a ser SHA-256 e o build roda sem
 * nenhuma flag especial, do Node 12 ate o Node 24.
 *
 * Decisao 2: o Vuetify e importado "completo".
 *
 * O Vuetify 2 pode ser usado a la carte (importando so os componentes
 * usados), o que exige o vuetify-loader e o sass, ou de forma completa, que
 * funciona com o CSS pronto. A forma completa foi escolhida: menos
 * dependencias, menos chance de erro de build e o mesmo resultado visual. O
 * tema continua customizavel pelo objeto "theme" do plugin Vuetify
 * (src/plugins/vuetify.js).
 *
 * Decisao 3: porta 8080 e host 0.0.0.0.
 *
 * A API roda na 8081 e o frontend na 8080 - duas origens diferentes, e por
 * isso o CORS do backend precisa estar configurado. O "host: 0.0.0.0"
 * permite acessar o servidor pelo IP da maquina na rede local, util para
 * testar no celular ou em outro computador.
 */
module.exports = {
  /**
   * Configuracao do servidor de desenvolvimento.
   */
  devServer: {
    port: 8080,
    host: '0.0.0.0',
    allowedHosts: 'all'
  },

  /**
   * "transpileDependencies" diz ao babel para reprocessar as dependencias
   * que vem em formato "moderno" (com sintaxe que o navegador ainda nao
   * entende nativamente). O Vuetify e um caso classico: sem isto, pode
   * aparecer erro de "Unexpected token" ao rodar a aplicacao.
   */
  transpileDependencies: [
    'vuetify'
  ],

  /**
   * Desliga o aviso de "performance" do Vue 2 em desenvolvimento.
   */
  productionSourceMap: false
};
