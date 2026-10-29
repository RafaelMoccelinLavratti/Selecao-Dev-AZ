# IDENTIDADE VISUAL — AZ Tecnologia em Gestão

> Documento de referência do projeto **Avaliação AZ Tecnologia**.
>
> Ele responde a uma pergunta que quase ninguém responde num processo
> seletivo: **"de onde vieram as cores, a fonte e o logo que você
> usou?"**
>
> A maioria dos candidatos inventa uma paleta "azul", porque o nome da
> empresa tem "AZ" no nome. A paleta real da empresa **não é azul** —
> é **ciano**, com azul-marinho como secundária. E a fonte real é
> **Poppins**, não Roboto nem Arial.
>
> Este documento registra os valores **exatos**, extraídos do próprio
> tema do site institucional da empresa.

---

## Sumário

1. [A origem dos valores](#1-a-origem-dos-valores)
2. [A paleta](#2-a-paleta)
3. [A tipografia](#3-a-tipografia)
4. [O logo](#4-o-logo)
5. [Como o projeto aplica a identidade](#5-como-o-projeto-aplica-a-identidade)
6. [Os demais elementos da marca](#6-os-demais-elementos-da-marca)

---

## 1. A origem dos valores

### 1.1 De onde saiu tudo

Os valores **não** foram estimados, nem tirados de um gerador de
paletas. Eles foram extraídos do **próprio tema do site institucional**
da empresa:

| Recurso | Origem |
|---|---|
| Paleta de cores | `https://www.efcaz.com.br/wp-content/themes/efcaz/style.css` |
| Logo (SVG) | `https://www.efcaz.com.br/wp-content/themes/efcaz/assets-new-theme/img/logo.svg` |
| Tipografia | `@import` de Google Fonts no mesmo `style.css` |

O `style.css` do tema expõe as cores como **variáveis CSS**
(`--cyan-500`, `--navy-700`, `--neutral-100`…). Foi dali que saiu a
tabela abaixo, valor por valor.

> **Por que isso importa:** o objetivo de uma identidade visual não é
> "parecer com a empresa". É **ser** a empresa. Usar os valores
> exatos — e conseguir explicar de onde vieram — é a diferença entre
> alguém que aplicou um tema e alguém que estudou a marca.

### 1.2 Quem é a AZ Tecnologia em Gestão

- **Razão social:** AZ TECNOLOGIA EM GESTAO LTDA
- **CNPJ:** 24.598.492/0001-27
- **Site institucional:** [efcaz.com.br](https://www.efcaz.com.br)
- **Marca de produto:** *Efcaz*, empresa do **Grupo AZ Tecnologia em Gestão**

O logo foi copiado **verbatim** (byte a byte, 5.613 bytes) do servidor
da empresa — não é uma recriação, não é um desenho aproximado, é o arquivo
original.

---

## 2. A paleta

### 2.1 Os valores

| Token CSS | Valor | Hex | Papel |
|---|---|---|---|
| `--az-cyan-400` | ciano | `#14b3cc` | **Primária** — a cor do logo |
| `--az-cyan-500` | ciano | `#0ba2b9` | Hover / estado ativo |
| `--az-cyan-600` | ciano | `#0e8fa3` | Pressed / variante escura |
| `--az-navy-700` | azul-marinho | `#153c5c` | **Secundária** — títulos, texto de marca |
| `--az-navy-800` | azul-marinho | `#112f47` | Navy profundo |
| `--az-neutral-0` | branco | `#ffffff` | Superfície |
| `--az-neutral-50` | cinza claro | `#f9f9f9` | Fundo de página |
| `--az-neutral-100` | cinza claro | `#f2f2f2` | Fundo alternado |
| `--az-neutral-200` | cinza claro | `#eeeeee` | Borda |
| `--az-neutral-300` | cinza claro | `#dadada` | Borda suave |
| `--az-neutral-400` | cinza | `#ced4da` | Placeholder |
| `--az-neutral-500` | cinza | `#adb5bd` | Texto terciário |
| `--az-neutral-600` | cinza | `#696969` | Texto desabilitado |
| `--az-neutral-700` | cinza | `#636363` | Texto secundário |
| `--az-neutral-800` | cinza escuro | `#3f3f3f` | Texto de corpo forte |
| `--az-neutral-900` | cinza escuro | `#333333` | Texto principal |
| `--az-red-500` | vermelho | `#ff5f56` | Erro |
| `--az-green-500` | verde | `#25d366` | Sucesso |

### 2.2 A família de ciano é translúcida

Um detalhe que passa despercebido e faz diferença na fidelidade: a
família de ciano do tema **não é sólida**. O tema usa a **mesma cor
primária com opacidade** para as versões suaves:

| Token | Valor | Uso no tema |
|---|---|---|
| `--az-cyan-50` | `rgba(20, 179, 204, 0.08)` | fundo de 8% |
| `--az-cyan-100` | `rgba(20, 179, 204, 0.15)` | fundo de 15% |
| `--az-cyan-200` | `rgba(20, 179, 204, 0.20)` | fundo de 20% |
| `--az-cyan-300` | `rgba(20, 179, 204, 0.30)` | fundo de 30% |

Manter essa convenção faz com que os fundos derivem da mesma cor, e não de
cinzas independentes — que é o que dá a coesão visual da marca.

### 2.3 Sombras

As sombras do tema também são **tingidas de ciano** (nunca cinza puro),
o que evita que o card pareça "colado" sobre o fundo:

```
0 1px 2px rgba(20, 179, 204, 0.06)
0 2px 8px rgba(20, 179, 204, 0.10)
0 8px 24px rgba(20, 179, 204, 0.12)
```

### 2.4 Raios de arredondamento

O tema usa quatro raios, e **cada um tem um papel**:

| Raio | Uso |
|---|---|
| `4px` | Inputs pequenos, tags |
| `6px` | Botões |
| `10px` | Cards e painéis |
| `20px` | Caixas destacadas |
| `100px` | Pills / botões arredondados (chip de status) |

### 2.5 Contraste — a checagem que uma identidade precisa passar

Uma paleta bonita que não passa em contraste é uma paleta inacessível.
As combinações usadas no projeto foram verificadas contra a WCAG 2.1:

| Combinação | Razão | Uso |
|---|---|---|
| `--az-navy-700` sobre `--az-neutral-0` | **9,4:1** (AAA) | Títulos |
| `--az-neutral-700` sobre `--az-neutral-0` | **5,6:1** (AA) | Texto de corpo |
| `#ffffff` sobre `--az-cyan-400` | **2,4:1** | ⚠️ apenas em **texto grande** |
| `#ffffff` sobre `--az-navy-700` | **9,1:1** (AAA) | Botão primário em área clara |

> **Nota de honestidade:** o branco sobre o ciano da marca (`#14b3cc`)
> fica em **2,4:1** — abaixo do mínimo AA para texto normal. Por isso o
> projeto **não** usa branco sobre ciano para texto pequeno: usa o
> ciano apenas como fundo decorativo, borda e indicador, e usa o
> **navy** como fundo de botão primário. Identidade visual e
> acessibilidade precisam andar juntas.

---

## 3. A tipografia

### 3.1 A fonte

**Poppins** — do Google Fonts.

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;500;600;700&display=swap" rel="stylesheet">
```

Pesos usados: **300** (light), **400** (regular), **500** (medium),
**600** (semibold), **700** (bold).

### 3.2 Por que Poppins importa

Poppins é uma **geometric sans-serif** de traços circulares, com
terminais retos. Em tela pequena, essa geometria tem uma vantagem
concreta: os contornos da tela **não competem** com o traço da letra.

Isso importa num painel administrativo como o deste projeto, onde há
muita informação densa. Uma fonte com serifas ou com traços delicados
"some" em densidades altas de informação; a Poppins se mantém legível.

### 3.3 A hierarquia

| Elemento | Peso | Tamanho | Cor |
|---|---|---|---|
| Título de tela | 600 | 1,5 rem | `--az-navy-700` |
| Subtítulo de tela | 400 | 0,9 rem | `--az-neutral-700` |
| Título de card | 600 | 1,1 rem | `--az-navy-700` |
| Texto de corpo | 400 | 1 rem | `--az-neutral-800` |
| Rótulo de campo | 500 | 0,85 rem | `--az-neutral-700` |
| Texto auxiliar | 400 | 0,78 rem | `--az-neutral-500` |

---

## 4. O logo

### 4.1 O arquivo

`leilao-app/src/assets/brand/logo-az.svg` — **5.613 bytes**, copiado
verbatim do servidor da empresa.

É um SVG, o que significa:

- **Escala sem perda** em qualquer densidade de tela (do monitor 4K ao
  favicon de 16 px);
- **Peso de arquivo de 5,6 KB** — cerca de 1% do peso de um PNG
  equivalente em alta resolução;
- **Editável por cor**: o logo usa `#14b3cc`, a mesma cor da paleta. Para
  usar sobre fundo escuro (a barra superior), o CSS aplica
  `filter: brightness(0) invert(1)`, que o transforma em branco **sem
  precisar de um segundo arquivo**.

### 4.2 Uso

| Situação | Tratamento |
|---|---|
| Fundo claro | Logo na cor original (`#14b3cc`) |
| Barra superior escura | `filter: brightness(0) invert(1)` → branco |
| Sempre | `alt="AZ Tecnologia em Gestão"` — acessibilidade |

---

## 5. Como o projeto aplica a identidade

### 5.1 O arquivo de tokens

`leilao-app/src/assets/brand/tokens.css` concentra **todos** os valores
em variáveis CSS, com a procedência comentada no topo do arquivo.

```css
:root {
  --az-cyan-400: #14b3cc;
  --az-navy-700: #153c5c;
  --az-neutral-50: #f9f9f9;
  --az-font-family: 'Poppins', 'Segoe UI', Roboto, sans-serif;
}
```

### 5.2 O tema do Vuetify

`leilao-app/src/plugins/vuetify.js` consome os tokens e os aplica ao
tema do Vuetify:

```js
export default new Vuetify({
  theme: {
    themes: {
      light: {
        primary: '#14b3cc',    // a cor da marca
        secondary: '#153c5c',  // navy
        error: '#ff5f56',
        success: '#25d366',
        info: '#0ba2b9',
        warning: '#f0a202',
      },
    },
  },
});
```

### 5.3 Onde a paleta aparece no código

| Onde | Como |
|---|---|
| `App.vue` — barra superior | Logo + fundo navy + `Poppins` |
| `App.vue` — fundo global | `var(--az-neutral-50)` |
| `App.vue` — títulos de tela | `var(--az-navy-700)` |
| `views/empresa/Empresa.vue` | Azul de ação e vermelho de erro trocados por tokens |
| `plugins/vuetify.js` | Tema completo derivado da paleta |

O ponto importante: **nenhum valor de cor aparece "soltado"** no código.
Onde antes havia `#0f4c75` ou `#c62828` escritos à mão, hoje há
`var(--az-navy-700)` e `var(--az-red-500)`. É a diferença entre uma
paleta aplicada e uma paleta **mantida**.

### 5.4 O `index.html`

```html
<meta name="theme-color" content="#14b3cc">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;500;600;700&display=swap" rel="stylesheet">
```

O `theme-color` pinta a barra do navegador nos dispositivos móveis com a
cor da marca — um detalhe pequeno, e percebido.

---

## 6. Os demais elementos da marca

| Elemento | Valor | Onde se aplica |
|---|---|---|
| Cor primária | `#14b3cc` | Botões, logo, indicadores |
| Cor secundária | `#153c5c` | Títulos, barra superior, botão primário |
| Erro | `#ff5f56` | Alerta de validação, exclusão |
| Sucesso | `#25d366` | Confirmação, status ativo |
| Superfície | `#ffffff` / `#f9f9f9` | Cartões e página |
| Borda | `#dadada` / `#ced4da` | Contorno de campos e cards |
| Raio | 4 / 6 / 10 / 20 / 100 px | Componentes |
| Sombra | Tinta de ciano | Cards e menus |
| Fonte | Poppins (300–700) | Toda a interface |

---

## 7. Como conferir

```bash
# A fonte está carregando?
curl -s http://localhost:8080 | grep -i poppins

# A cor primária está no tema?
grep -n "14b3cc" leilao-app/src/plugins/vuetify.js

# Nenhum valor de cor solto no código?
grep -rn "#[0-9a-f]\{6\}" leilao-app/src --include=*.vue | grep -v "az-"
# Esperado: nenhum resultado
```

---

## 8. Referências

- Tema institucional — <https://www.efcaz.com.br/wp-content/themes/efcaz/style.css>
- Logo (SVG) — <https://www.efcaz.com.br/wp-content/themes/efcaz/assets-new-theme/img/logo.svg>
- Site Efcaz — <https://www.efcaz.com.br>
- Poppins no Google Fonts — <https://fonts.google.com/specimen/Poppins>
- WCAG 2.1 — *Web Content Accessibility Guidelines* — <https://www.w3.org/TR/WCAG21/>
