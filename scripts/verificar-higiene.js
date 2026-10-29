/**
 * ============================================================================
 *  VERIFICADOR DE HIGIENE DO CODIGO (scripts/verificar-higiene.js)
 * ============================================================================
 *
 *  Roda com:  node scripts/verificar-higiene.js
 *
 *  POR QUE ESTE SCRIPT EXISTE?
 *  Porque um projeto se avalia tambem pelo cuidado com o texto. Erros de
 *  digitacao em comentarios - palavras em ingles que sobraram de um
 *  exemplo, ou caracteres de outro alfabeto que entraram por engano -
 *  passam despercebidos na hora de escrever e chamam atencao na hora
 *  de apresentar.
 *
 *  O QUE ELE PROCURA
 *  1) CARACTERES DE OUTRO ALFABETO (cirilico, CJK, hangul). Acentos do
 *     portugues sao corretos e NAO sao apontados.
 *  2) PALAVRAS EM INGLES QUE NAO DEVERIAM ESTAR AQUI - uma lista
 *     curia e especifica, para nao gerar ruido com palavras inglesas
 *     legitimamente usadas em codigo (findAll, save, delete...).
 *  3) ERROS DE DIGITACAO CONHECIDOS, como palavras coladas sem espaco.
 *
 *  Este script NAO faz parte do build. E uma ferramenta de apoio para
 *  a revisao antes da apresentacao.
 */
const fs = require('fs');
const path = require('path');

/** Pastas que nao contem codigo nosso. */
const IGNORADOS = ['node_modules', 'target', 'dist', '.git', '.idea', 'scripts'];

/** Extensoes de arquivo analisadas. */
const EXTENSOES = ['.java', '.vue', '.js', '.properties', '.md', '.sql', '.xml', '.yml'];

/**
 * Caracteres de outros alfabetos: cirilico, CJK e hangul.
 * Acentos latinos (acentuacao do portugues) NAO sao apontados.
 */
const RE_ALFABETO_ESTRANHO = /[Ѐ-ӿ　-鿿가-힯]/;

/**
 * Palavras em ingles que nao pertencem a este projeto.
 * A lista e curta e especifica de proposito - para nao acusar metodos
 * Java legitimos como "findAll" ou "save".
 */
const PALAVRAS_ESTRANGEIRAS = [
  'guarantees',
  'Transformation',
  'exactly',
  'StuckTogether',
  'ensure that',
  'should be',
  'the same as',
  'in order to',
  'it is possible',
  'we need to',
  'this is a',
  'you can see',
  'note that',
];

/**
 * Erros de digitacao conhecidos: uma palavra minuscula colada com uma
 * maiuscula no meio (ex.: "semPossibilidade").
 *
 * O filtro exige que a sequencia NAO seja um identificador de codigo
 * (que contem "(" ou ")" perto) - para nao acusar "findAll" e afins.
 */
const RE_STUCK = /\b[a-z]{3,}[A-Z][a-zA-Z]*\b/g;

/**
 * Palavras em camelCase que sao LEGITIMAS neste projeto e por isso
 * nao devem ser acusadas: nomes de campos, de metodos do framework e
 * de variaveis citadas em documentacao.
 */
const PALAVRAS_PERMITIDAS = [
  'authenticationDetailsSource', 'empresaId', 'findAll', 'inicioPrevisto',
  'leilaoId', 'mappedBy', 'nomeDaColuna', 'numeroLote', 'razaoSocial',
  'realmName', 'registrosAtivos', 'removeIf', 'securityFilterChain',
  'tipoDaAcao', 'valorInicial', 'authenticationEntryPoint', 'valueOf', 'findById', 'onlyExplicitlyIncluded', 'existsBy',
];

function listarArquivos(dir, resultado = []) {
  for (const entrada of fs.readdirSync(dir, { withFileTypes: true })) {
    if (IGNORADOS.includes(entrada.name)) continue;
    const caminho = path.join(dir, entrada.name);
    if (entrada.isDirectory()) {
      listarArquivos(caminho, resultado);
    } else if (EXTENSOES.includes(path.extname(entrada.name))) {
      resultado.push(caminho);
    }
  }
  return resultado;
}

const raiz = path.resolve(__dirname, '..');
const problemas = [];

for (const arquivo of listarArquivos(raiz)) {
  const linhas = fs.readFileSync(arquivo, 'utf-8').split('\n');

  // Em arquivos de documentacao, o codigo dentro de blocos cercados
  // por ``` nao e prosa - e identico ao codigo do projeto, entao nao
  // deve ser analisado pela regra de "palavra colada".
  let dentroDeBlocoDeCodigo = false;

  linhas.forEach((linhaOriginal, indice) => {
    const numero = indice + 1;

    // Em Markdown, um bloco citado (">") prefixa cada linha. Tiramos o
    // prefixo para que as verificacoes vejam o conteudo real.
    const linha = linhaOriginal.replace(/^\s*>\s?/, '');

    // ---- 0) Controla a entrada e saida de blocos de codigo ----
    if (arquivo.endsWith('.md')) {
      if (linha.trim().startsWith('```')) {
        dentroDeBlocoDeCodigo = !dentroDeBlocoDeCodigo;
        return;
      }
      if (dentroDeBlocoDeCodigo) {
        return;
      }
    }

    // ---- 1) Caracteres de outro alfabeto ----
    if (RE_ALFABETO_ESTRANHO.test(linha)) {
      problemas.push({
        arquivo, numero, tipo: 'ALFABETO-ESTRANHO',
        detalhe: linha.trim().slice(0, 110)
      });
    }

    // ---- 2) Palavras em ingles ----
    const minuscula = linha.toLowerCase();
    for (const palavra of PALAVRAS_ESTRANGEIRAS) {
      if (minuscula.includes(palavra.toLowerCase())) {
        problemas.push({
          arquivo, numero, tipo: 'INGLES',
          detalhe: palavra + ' -> ' + linha.trim().slice(0, 90)
        });
        break;
      }
    }

    // ---- 3) Palavras coladas (erro de digitacao) ----
    // A verificacao so vale em TEXTO PROSE. Em codigo, "findAll" e
    // "schemaLocation" sao nomes legitimos; em XML, todas as tags sao
    // assim. Por isso, so analisamos linhas de comentario e arquivos
    // de documentacao.
    const ehComentario = /^(\*|\/\/|\/\*|<!--)/.test(linha.trim());
    const ehMarkdown = arquivo.endsWith('.md');
    const ehTexto = ehComentario || (ehMarkdown && !linha.includes('`'));

    if (ehTexto) {
      const stuck = linha.match(RE_STUCK);
      if (stuck) {
        for (const palavra of stuck) {
          // Identificadores citados entre crases, entre aspas ou em tags
          // Javadoc (@param, @return) sao codigo, nao texto corrido.
          const ehCodigo = linha.includes('`' + palavra + '`')
            || linha.includes('"' + palavra + '"')
            || linha.includes('@param ' + palavra)
            || linha.includes('@return ' + palavra)
            || linha.includes('@throws ' + palavra)
            || new RegExp('\\b' + palavra + '\\s*\\(').test(linha)
            || new RegExp('\\.' + palavra + '\\b').test(linha);

          if (!ehCodigo && !PALAVRAS_PERMITIDAS.includes(palavra)) {
            problemas.push({
              arquivo, numero, tipo: 'COLADA',
              detalhe: palavra + ' -> ' + linha.trim().slice(0, 90)
            });
            break;
          }
        }
      }
    }
  });
}

if (problemas.length === 0) {
  console.log('Higiene OK: nenhum problema encontrado.');
  process.exit(0);
}

console.log('Problemas encontrados: ' + problemas.length + '\n');
for (const p of problemas) {
  console.log(`[${p.tipo}] ${path.relative(raiz, p.arquivo)}:${p.numero}`);
  console.log('    ' + p.detalhe);
}
process.exit(1);
