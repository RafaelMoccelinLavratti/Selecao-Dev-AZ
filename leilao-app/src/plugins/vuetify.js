/**
 * Configuracao do Vuetify.
 *
 * O Vuetify e a biblioteca de componentes que traz os controles que o
 * enunciado exige (o v-data-table, que e a tabela com busca, ordenacao e
 * paginacao).
 *
 * As cores do tema nao foram escolhidas por gosto pessoal: foram extraidas
 * do proprio tema do site institucional da AZ Tecnologia em Gestao.
 */
import Vue from 'vue';
import Vuetify from 'vuetify';

// Importa o CSS do Vuetify (versao completa, ja com todos os componentes).
import 'vuetify/dist/vuetify.min.css';

// Importa a fonte dos icones Material Design Icons.
import '@mdi/font/css/materialdesignicons.css';

// Os tokens de marca ficam disponiveis como variaveis CSS globais
// (--az-laranja-500, --az-azul-900, ...).
import '@/assets/brand/tokens.css';

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
   *
   * As cores abaixo sao as MESMAS do arquivo de tokens. O Vuetify precisa
   * delas em JS (porque as gera em JavaScript), entao o valor esta
   * duplicado aqui e em tokens.css. Essa duplicidade e consciente: o
   * tokens.css e a fonte da verdade para o CSS puro, e este bloco e a
   * fonte da verdade para o que o Vuetify gera em JS. As duas entradas
   * apontam para o mesmo valor, e o comentario em tokens.css registra a
   * origem dos dois.
   */
  theme: {
    options: {
      // Aplica as cores customizadas nos componentes que tem "variant"
      // definido pelo Vuetify (botoes, chips, abas ativas).
      customProperties: true
    },
    themes: {
      light: {
        // LARANJA DE MARCA - cor de acao (botao principal, link).
        primary: '#EA4F24',
        // Variacao mais escura, usada no estado "pressed".
        primaryDark: '#D4401A',
        // AZUL-MARINHO DE ESTRUTURA - navbar, rodape, superficies escuras.
        secondary: '#002436',
        // Um pouco mais claro, para superficies de apoio.
        accent: '#0D4A68',
        // Vermelho de erro.
        error: '#DC3545',
        // Azul de aviso.
        info: '#0D4A68',
        // Verde de sucesso.
        success: '#198754',
        // Amarelo de atencao.
        warning: '#FFC107'
      }
    }
  }
});
