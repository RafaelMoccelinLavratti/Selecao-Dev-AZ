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
 *  As cores deste tema NAO foram escolhidas por gosto pessoal. Elas foram
 *  extraidas do proprio tema do site institucional da AZ Tecnologia em
 *  Gestao (azi.com.br, tema "aztheme"), das regras CSS:
 *
 *      https://www.azi.com.br/wp-content/themes/aztheme/style.css
 *
 *  As duas cores de marca sao:
 *
 *      .bg-primary   { background-color: #EA4F24; }   <- laranja
 *      .bg-secondary { background-color: #002436; }   <- azul-marinho
 *
 *  O logo que usamos e o arquivo original da empresa:
 *
 *      https://www.azi.com.br/wp-content/uploads/2023/06/logo_az_white.svg
 *
 *  Ele e' branco (o site o usa sobre a navbar azul-marinho) - por isso,
 *  na barra superior escura da aplicacao ele ja aparece na cor certa,
 *  sem nenhum filtro.
 *
 *  A tipografia institucional tem DUAS fontes: **Barlow** no corpo do
 *  texto e **Tomorrow** nos titulos (h1 ate h5). As duas entram pelo
 *  Google Fonts em public/index.html.
 *
 *  O codigo hexadecimal completo de cada cor, com o comentario de onde
 *  veio, esta em src/assets/brand/tokens.css.
 *
 *  ---------------------------------------------------------------------------
 *  POR QUE LARANJA E AZUL-MARINHO?
 *  ---------------------------------------------------------------------------
 *  Sao as duas cores de maior peso no tema da empresa. O laranja (#EA4F24)
 *  e' a cor de ACAO - botao principal, link, item ativo de menu. O
 *  azul-marinho (#002436) e' a cor de ESTRUTURA - barra de navegacao,
 *  rodape, superficies escuras.
 *
 *  Repare que o tema INVERTE no hover: o link normal e' laranja e, ao
 *  passar o mouse, vira azul-marinho. E' a mesma logica aqui: a acao
 *  "acende" em laranja, e o descanso fica no azul.
 *
 *  Importante para acessibilidade: o laranja #EA4F24 sobre branco tem
 *  contraste de aproximadamente 3,7:1 - atende o minimo de 3:1 da WCAG
 *  AA para texto GRANDE, mas nao o de 4,5:1 para texto normal. Por isso
 *  o laranja nunca e' usado como cor de texto pequeno: ele e' cor de
 *  fundo de botao (com texto branco, 3,7:1) ou de elemento grafico.
 *  Para texto, usamos o cinza #575756 (7,2:1, AAA) e o azul-marinho
 *  #002436 (16,1:1, AAA).
 * ============================================================================
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
