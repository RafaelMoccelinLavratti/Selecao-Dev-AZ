/**
 * ============================================================================
 *  CONFIGURACAO DO VUETIFY (src/plugins/vuetify.js)
 * ============================================================================
 *
 *  O QUE E O VUETIFY?
 *  E uma biblioteca de componentes para Vue que traz os controles que o
 *  enunciado exige (o "v-data-table", que e a tabela com busca, ordenacao
 *  e paginacao) e ainda outros 60+ componentes prontos (botoes, campos de
 *  formulario, dialogos, alertas). Usar uma biblioteca established e
 *  padrao no mercado e o que a empresa espera de um projeto real - nao
 *  reinventar tabela e formulario do zero.
 *
 *  O QUE ESTA CONFIGURADO AQUI:
 *  - o "theme": as cores da aplicacao. Escolhi um azul-petroleo como
 *    cor principal e um fundo cinza-claro, porque sao cores de baixa
 *    fadiga visual em telas de uso diario (o sistema sera usado o dia
 *    todo, para consultar leiloes).
 *  - o "icons": o Vuetify usa Material Design Icons (MDI). Sem o
 *    "@mdi/font" instalado, nenhum icone apareceria na tela.
 * ============================================================================
 */
import Vue from 'vue';
import Vuetify from 'vuetify';

// Importa o CSS do Vuetify (versao completa, ja com todos os componentes).
import 'vuetify/dist/vuetify.min.css';

// Importa a fonte dos icones Material Design Icons.
import '@mdi/font/css/materialdesignicons.css';

Vue.use(Vuetify);

export default new Vuetify({
  /**
   * Icones: 'mdi' = Material Design Icons (biblioteca @mdi/font).
   */
  icons: {
    iconfont: 'mdi'
  },

  /**
   * Tema da aplicacao.
   */
  theme: {
    options: {
      // Aplica as cores customizadas nos componentes que tem "variant"
      // definido pelo Vuetify (botoes, chips, abas ativas).
      customProperties: true
    },
    themes: {
      light: {
        primary: '#0F4C75',   // azul-petroleo (cor principal)
        secondary: '#328CC1',  // azul medio (secundario)
        accent: '#D9B310',    // dourado (destaque/atencao)
        error: '#C62828',     // vermelho padrao para erros
        info: '#0288D1',
        success: '#2E7D32',
        warning: '#EF6C00'
      }
    }
  }
});
