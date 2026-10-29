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
 *  ---------------------------------------------------------------------------
 *  A IDENTIDADE VISUAL NAO FOI INVENTADA: FOI EXTRAIDA
 *  ---------------------------------------------------------------------------
 *  As cores deste tema NAO foram escolhidas por gosto pessoal. elas foram
 *  extraidas do proprio tema do site institucional da AZ Tecnologia em
 *  Gestao (efcaz.com.br), das variaveis CSS:
 *
 *      https://www.efcaz.com.br/wp-content/themes/efcaz/style.css
 *
 *  A cor principal (#14b3cc) e a MESMA cor do logo SVG da empresa - o que
 *  faz a interface parecer com a marca sem nenhum esforco, porque a paleta
 *  ja era coerente.
 *
 *  A fonte institucional e a **Poppins** (Google Fonts). Ela e carregada
 *  em public/index.html e aplicada nos estilos globais de App.vue.
 *
 *  O codigo hexadecimal completo de cada cor, com o comentario de onde
 *  veio, esta em src/assets/brand/tokens.css.
 *
 *  ---------------------------------------------------------------------------
 *  POR QUE A PRIMARIA E CIANO E A SECUNDARIA E NAVY?
 *  ---------------------------------------------------------------------------
 *  Sao as duas cores de maior peso no tema da empresa. O ciano (#14b3cc)
 *  e a cor de marca e usa acao (botoes, abas ativas, foco). O navy
 *  (#153c5c) e a cor de estrutura e usa texto e superficies escuras.
 *
 *  Importante para acessibilidade: o ciano #14b3cc sobre branco tem
 *  contraste de aproximadamente 2.4:1 - ABAIXO do minimo de 4.5:1 da
 *  WCAG AA para texto normal. Por isso o ciano nunca e usado como cor
 *  de texto pequeno: ele e a cor de fundo de botao (com texto branco) ou
 *  de elemento grafico. Para texto, usamos o navy e o neutral-900, que
 *  passam folgadamente no contraste. O tema original da empresa usa a
 *  mesma logica.
 * ============================================================================
 */
import Vue from 'vue';
import Vuetify from 'vuetify';

// Importa o CSS do Vuetify (versao completa, ja com todos os componentes).
import 'vuetify/dist/vuetify.min.css';

// Importa a fonte dos icones Material Design Icons.
import '@mdi/font/css/materialdesignicons.css';

// Os tokens de marca ficam disponiveis como variaveis CSS globais
// (--az-cyan-400, --az-navy-700, ...).
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
        // CIANO DE MARCA - mesma cor do logo SVG da AZ.
        primary: '#14b3cc',
        // Variacao mais escura do ciano, usada no estado "pressed".
        primaryDark: '#0e8fa3',
        // NAVY DE ESTRUTURA - cor de apoio da marca.
        secondary: '#153c5c',
        // NAVY MAIS ESCURO - usado em superficies de alta hierarquia.
        accent: '#112f47',
        // Vermelho de erro da marca.
        error: '#ff5f56',
        // Azul de aviso (mesmo valor do tema institucional).
        info: '#0ba2b9',
        // Verde de sucesso da marca.
        success: '#25d366',
        // Laranja de atencao.
        warning: '#ed781c'
      }
    }
  }
});
