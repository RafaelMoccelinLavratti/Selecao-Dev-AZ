/**
 * ============================================================================
 *  CONFIGURACAO DO VUE CLI (vue.config.js)
 * ============================================================================
 *
 *  PARA QUE SERVE ESTE ARQUIVO?
 *  O Vue CLI (ferramenta de build) le este arquivo para saber como
 *  compilar e servir o frontend.
 *
 *  ------------------------------------------------------
 *  DECISAO 1: POR QUE O VUE CLI 5 E NAO O 4 (que vinha no starter)?
 *  ------------------------------------------------------
 *  O starter usava @vue/cli-service 4, que empacota o webpack 4. O webpack 4
 *  calcula o hash dos arquivos usando o algoritmo MD4 do OpenSSL - e o
 *  OpenSSL 3 (padrao em Linux e a partir do Node 17) REMOVEU esse
 *  algoritmo. Resultado: ao rodar "npm run serve" em uma maquina atual,
 *  o build quebra com o erro:
 *
 *      error:0308010C:digital envelope routines::unsupported
 *      (ERR_OSSL_EVP_UNSUPPORTED)
 *
 *  A solucao mais comum seria "--openssl-legacy-provider", mas ela so
 *  existe a partir do Node 17 (em versoes anteriores, o Node reclama do
 *  parametro desconhecido) e descontinua no Node 22+.
 *
 *  A solucao DEFINITIVA e subir para o Vue CLI 5, que usa o webpack 5.
 *  O webpack 5 usa o hash SHA-256 (ainda disponivel) e roda sem nenhuma
 *  flag especial, do Node 12 ate o Node 24. Alem disso, o webpack 5 tem
 *  tree-shaking melhor e build mais rapido.
 *
 *  ------------------------------------------------------
 *  DECISAO 2: POR QUE O VUETIFY E IMPORTADO "COMPLETO"?
 *  ------------------------------------------------------
 *  O Vuetify 2 pode ser usado de duas formas:
 *    - "a la carte" (importar so os componentes usados): exige o
 *      vuetify-loader e o sass, e a configuracao de cores fica em
 *      arquivos .scss;
 *    - "completo" (importar a biblioteca inteira): funciona com o CSS
 *      pronto, sem sass, sem loader.
 *  Escolhi a forma completa: menos dependencias, menos chance de erro de
 *  build e o mesmo resultado visual. O tema continua customizavel pelo
 *  objeto "theme" do plugin Vuetify (src/plugins/vuetify.js).
 *
 *  ------------------------------------------------------
 *  DECISAO 3: POR QUE A PORTA E A 8080 E O "HOST" E A 0.0.0.0?
 *  ------------------------------------------------------
 *  A API roda na 8081 e o frontend na 8080 (as duas em portas diferentes e,
 *  por isso, o CORS configurado no backend e necessario).
 *  "host: 0.0.0.0" permite acessar o servidor por IP da maquina na rede
 *  local - util para testar no celular ou em outro computador.
 * ============================================================================
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
