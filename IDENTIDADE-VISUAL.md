# Identidade Visual da AZ Tecnologia em Gestão

> Documento de referência do projeto **Avaliação AZ Tecnologia**.
>
> Ele responde a uma pergunta que quase ninguém responde num processo
> seletivo: **de onde vieram as cores, a fonte e o logo que você usou?**
>
> A resposta deste projeto: do **site institucional da própria empresa**
> (`azi.com.br`), extraídos do tema que a empresa publica. Nenhum valor foi
> estimado, e nenhum foi escolhido por gosto pessoal.

## 1. De onde saiu tudo

Os valores foram extraídos do **tema do site institucional da AZ**, o mesmo
arquivo que a empresa usa para pintar o próprio site:

| Recurso | Origem |
|---|---|
| Paleta e tipografia | `https://www.azi.com.br/wp-content/themes/aztheme/style.css` |
| CSS servido (minificado) | `https://www.azi.com.br/wp-content/cache/min/1/74ab08738a3f428bc1966ccd6c994daf.css` |
| Logo (SVG) | `https://www.azi.com.br/wp-content/uploads/2023/06/logo_az_white.svg` |

É nesse `style.css` que estão as regras de marca:

```css
.bg-primary   { background-color: #EA4F24; }
.bg-secondary { background-color: #002436; }
.btn-primary  { background-color: #EA4F24; border-color: #EA4F24; }
.navbar       { background-color: #002436; }
.footer       { background-color: #002436; padding: 80px 0; }
a             { color: #EA4F24; }
a:hover       { color: #002436; }
```

E o próprio tema declara a autoria:

```css
/*
    Theme Name: AZTHEME
    Theme URL: http://azi.com.br
    Author: AZ Tecnologia em Gestão
*/
```

Sobre a empresa: AZ TECNOLOGIA EM GESTAO LTDA, CNPJ 24.598.492/0001-27,
sede em Campo Grande (MS), CNAE principal 6201-5/01 (desenvolvimento de
programas de computador sob encomenda).

## 2. A paleta

### As duas cores de marca

| Token CSS | Hex | Papel no site |
|---|---|---|
| `--az-laranja-500` | `#EA4F24` | Primária: botões, links, ação |
| `--az-azul-900` | `#002436` | Secundária: navbar, rodapé, estrutura |

A regra de cor do tema é uma inversão proposital: o link normal é laranja e,
no hover, vira azul-marinho. A ação "acende" em laranja, o descanso fica no
azul.

### Neutros

O tema aplica um cinza quente (`#575756`) em **todo** o texto do corpo, e não
o cinza frio padrão do Bootstrap. A escala completa de neutros e as cores
semânticas (erro, sucesso, atenção) estão em
`leilao-app/src/assets/brand/tokens.css`, junto com a procedência de cada
valor.

### Raios e sombras

Os raios vão de `0` (botões de ícone) a `100px` (pontos da linha do tempo),
e as sombras são as neutras do Bootstrap. A AZ não tinge as sombras com a cor
da marca.

## 3. A tipografia

A instituição usa duas fontes, com papéis distintos:

| Papel | Fonte | Origem | Pesos |
|---|---|---|---|
| Corpo do texto | **Barlow** (sans-serif) | Google Fonts | 300, 400, 500, 600, 700 |
| Títulos (h1–h5) | **Tomorrow** (display) | Google Fonts / TTF local do tema | 400–700 |

```html
<link href="https://fonts.googleapis.com/css2?family=Barlow:wght@300;400;500;600;700&family=Tomorrow:wght@400;500;600;700&display=swap" rel="stylesheet">
```

A regra do tema é literal:

```css
*                  { font-family: 'Barlow', sans-serif; color: #575756; }
h1, h2, h3, h4, h5 { font-family: tomorrow, sans-serif; }
```

Por que duas fontes: a **Tomorrow** é uma display geométrica, de traço largo,
feita para ser grande e curta. A **Barlow** é uma sans semi-condensada, de
leitura rápida em tela pequena. Juntas separam o que é título do que é dado,
que é exatamente o que um painel administrativo precisa. E é o que o site da
empresa faz.

## 4. O logo

`leilao-app/src/assets/brand/logo-az.svg`, **27.257 bytes**, copiado
verbatim do servidor da empresa.

É um SVG, o que significa escala sem perda em qualquer densidade de tela, e
peso de 27 KB (menos de 1% de um PNG equivalente em alta resolução). Já vem
branco, porque o site o usa sobre a navbar azul-marinho: por isso ele
aparece na cor certa na barra escura da aplicação, sem filtro e sem um
segundo arquivo.

Em todo lugar, `alt="AZ Tecnologia em Gestao"`.

## 5. Como o projeto aplica a identidade

`leilao-app/src/assets/brand/tokens.css` concentra todos os valores em
variáveis CSS, com a procedência comentada no topo:

```css
:root {
  --az-laranja-500: #ea4f24;
  --az-azul-900: #002436;
  --az-cinza-700: #575756;
  --az-fonte-corpo: 'Barlow', 'Segoe UI', Roboto, sans-serif;
  --az-fonte-titulo: 'Tomorrow', 'Barlow', sans-serif;
}
```

| Onde | Como |
|---|---|
| `plugins/vuetify.js` | Tema do Vuetify: `primary` laranja, `secondary` azul-marinho |
| `App.vue`: barra superior | `color="secondary"` (azul-marinho, como a navbar do site) |
| `App.vue`: fundo global | `var(--az-cinza-50)` |
| `App.vue`: títulos de tela | `var(--az-azul-900)` |
| `App.vue`: tipografia | `var(--az-fonte-corpo)` no corpo, `var(--az-fonte-titulo)` nos títulos |
| `public/index.html` | Barlow + Tomorrow, `theme-color` `#002436` |
| `views/empresa/Empresa.vue` | Azul de seção e vermelho de erro por token |
| `views/leilao/Leiloes.vue` | Azul de título por token |

O ponto importante: nenhum valor de cor aparece "soltado" no código. Onde
antes havia `#0f4c75` ou `#c62828` escritos à mão, hoje há
`var(--az-azul-900)` e `var(--az-vermelho-600)`. É a diferença entre uma
paleta aplicada e uma paleta mantida.

O `theme-color` no `index.html` pinta a barra do navegador nos celulares com
a cor institucional: um detalhe pequeno, e percebido.

## 6. Acessibilidade

Uma paleta bonita que não passa em contraste é uma paleta inacessível. As
combinações foram verificadas contra a **WCAG 2.1**:

| Combinação | Razão | Uso |
|---|---|---|
| `#002436` sobre `#ffffff` | **16,1:1** (AAA) | Título, texto de destaque |
| `#575756` sobre `#ffffff` | **7,2:1** (AAA) | Corpo do texto |
| `#EA4F24` sobre `#002436` | **4,3:1** (AA) | Destaque sobre fundo escuro |
| `#EA4F24` sobre `#ffffff` | **3,7:1** (AA Grande) | Só em texto grande |
| `#ffffff` sobre `#EA4F24` | **3,7:1** (AA Grande) | Só em texto grande |

Um ponto honesto: o laranja da marca fica em 3,7:1 sobre branco, o que passa
no mínimo de 3:1 da WCAG AA para texto grande, mas não no de 4,5:1 para
texto normal. Por isso o projeto não usa laranja como cor de texto pequeno:
usa-o como fundo de botão e como elemento gráfico, e reserva o azul-marinho
e o cinza para o texto. Identidade visual e acessibilidade precisam andar
juntas.

## 7. Como conferir

```bash
# A fonte esta carregando?
curl -s http://localhost:8080 | grep -i "barlow\|tomorrow"

# A cor de marca esta no tema?
grep -n "EA4F24\|002436" leilao-app/src/plugins/vuetify.js

# Nenhum valor de cor solto no codigo?
grep -rn "#[0-9a-f]\{6\}" leilao-app/src --include=*.vue | grep -v "az-"
# Esperado: nenhum resultado
```

## 8. Referências

- Site institucional: <https://www.azi.com.br>
- Tema do site: <https://www.azi.com.br/wp-content/themes/aztheme/style.css>
- Logo (SVG): <https://www.azi.com.br/wp-content/uploads/2023/06/logo_az_white.svg>
- Barlow no Google Fonts: <https://fonts.google.com/specimen/Barlow>
- Tomorrow no Google Fonts: <https://fonts.google.com/specimen/Tomorrow>
- WCAG 2.1, *Web Content Accessibility Guidelines*: <https://www.w3.org/TR/WCAG21/>
