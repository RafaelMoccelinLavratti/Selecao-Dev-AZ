/**
 * ============================================================================
 *  MASCARAS E VALIDADORES (src/utils/mascaras.js)
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE E UMA "MASCARA"?
 *  ------------------------------------------------------
 *  E uma funcao que formata o texto enquanto o usuario digita:
 *      "11422377000155"  ->  "11.422.377/0001-55"
 *      "01310100"        ->  "01310-100"
 *      "1130001100"      ->  "(11) 3000-1100"
 *
 *  Deixar o campo aceitar qualquer coisa e correr o risco de o usuario
 *  digitar o numero errado. Com a mascara, o formato fica visivel
 *  enquanto ele digita, e o erro e praticamente impossivel.
 *
 *  ------------------------------------------------------
 *  POR QUE A VALIDACAO EXISTE AQUI E NO BACKEND?
 *  ------------------------------------------------------
 *  Aqui (frontend) a validacao e CONVENIENCIA: da erro na hora, mostra
 *  o campo em vermelho e nao perde o que ja foi digitado.
 *
 *  No backend a validacao e GARANTIA: e a camada que nao pode ser
 *  contornada, porque qualquer um pode chamar a API sem passar pela
 *  tela. As duascamadas usam as MESMAS regras (as expressoes regulares
 *  aqui espelham as anotacoes @Pattern do Java), para que o usuario
 *  nunca veja "a tela aceitou, mas a API recusou".
 * ============================================================================
 */

/**
 * Deixa apenas digitos no texto.
 */
export function somenteDigitos(valor) {
  if (!valor) {
    return '';
  }
  return String(valor).replace(/\D/g, '');
}

/**
 * MASCARA DE CNPJ: 00.000.000/0000-00
 *
 * @param {string} valor texto digitado pelo usuario
 * @returns {string} texto formatado
 */
export function mascaraCnpj(valor) {
  const digitos = somenteDigitos(valor).slice(0, 14);

  let formatado = digitos;
  if (digitos.length > 12) {
    formatado = digitos.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5');
  } else if (digitos.length > 8) {
    formatado = digitos.replace(/(\d{2})(\d{3})(\d{3})(\d{1,4})/, '$1.$2.$3/$4');
  } else if (digitos.length > 6) {
    formatado = digitos.replace(/(\d{2})(\d{3})(\d{1,3})/, '$1.$2.$3');
  } else if (digitos.length > 3) {
    formatado = digitos.replace(/(\d{2})(\d{1,3})/, '$1.$2');
  } else if (digitos.length > 2) {
    formatado = digitos.replace(/(\d{2})/, '$1');
  }

  return formatado;
}

/**
 * MASCARA DE CEP: 00000-000
 */
export function mascaraCep(valor) {
  const digitos = somenteDigitos(valor).slice(0, 8);

  if (digitos.length > 5) {
    return digitos.replace(/(\d{5})(\d{1,3})/, '$1-$2');
  }
  return digitos;
}

/**
 * MASCARA DE TELEFONE: (00) 0000-0000 ou (00) 00000-0000
 */
export function mascaraTelefone(valor) {
  const digitos = somenteDigitos(valor).slice(0, 11);

  if (digitos.length > 10) {
    return digitos.replace(/(\d{2})(\d{5})(\d{1,4})/, '($1) $2-$3');
  }
  if (digitos.length > 6) {
    return digitos.replace(/(\d{2})(\d{4})(\d{1,4})/, '($1) $2-$3');
  }
  if (digitos.length > 2) {
    return digitos.replace(/(\d{2})(\d{1,5})/, '($1) $2');
  }
  return digitos;
}

/**
 * MASCARA DE DINHEIRO: 1.234,56
 *
 * Repare que usamos VIRGULA como separador decimal, que e o padrao
 * brasileiro (o codigo do banco e o mesmo).
 */
export function mascaraDinheiro(valor) {
  const digitos = somenteDigitos(valor);

  if (!digitos) {
    return '';
  }

  const numero = (Number(digitos) / 100).toFixed(2);
  const partes = numero.split('.');
  const inteiro = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');

  return inteiro + ',' + partes[1];
}

/**
 * ------------------------------------------------------------------
 *  VALIDADORES
 *  Sao as mesmas regras aplicadas no backend (arquivo
 *  EmpresaRequest.java). Cada um devolve true (valido) ou false.
 * ------------------------------------------------------------------
 */

/** Campo obrigatorio: nao pode ser vazio nem apenas espacos. */
export function obrigatorio(valor) {
  return !!valor && String(valor).trim().length > 0;
}

/** E-mail no formato algo@algo.algo. */
export function emailValido(valor) {
  if (!valor) {
    return true; // campo opcional: vazio e aceito
  }
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(valor).trim());
}

/** URL que comeca com http:// ou https://. */
export function urlValida(valor) {
  if (!valor) {
    return true; // campo opcional
  }
  return /^https?:\/\/.+/.test(String(valor).trim());
}

/** CNPJ com 14 digitos. */
export function cnpjValido(valor) {
  if (!valor) {
    return true; // campo opcional
  }
  return somenteDigitos(valor).length === 14;
}

/** CEP com 8 digitos. */
export function cepValido(valor) {
  if (!valor) {
    return true;
  }
  return somenteDigitos(valor).length === 8;
}

/** Telefone com 10 ou 11 digitos. */
export function telefoneValido(valor) {
  if (!valor) {
    return true;
  }
  const digitos = somenteDigitos(valor).length;
  return digitos === 10 || digitos === 11;
}

/**
 * Verifica o tamanho maximo de um texto (limite de coluna no banco).
 */
export function tamanhoValido(valor, maximo) {
  if (!valor) {
    return true;
  }
  return String(valor).length <= maximo;
}
