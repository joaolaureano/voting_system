// Verificacao de inclusao da RFC 6962, sem dependencia nenhuma.
//
// Este arquivo e o ponto do frontend inteiro. Tudo aqui roda no navegador do eleitor: o
// servidor entrega o recibo, a prova e a raiz, e quem decide se a conta fecha e esta funcao,
// na maquina de quem pergunta. Um servidor que mentisse teria de forjar SHA-256.
//
// E o mesmo algoritmo de pkg/merkle/proof.go, na mesma ordem de operacoes. Se os dois
// discordarem, um dos dois esta errado - e por isso os testes usam os vetores que o Go
// produz, e nao vetores inventados aqui.
//
// Nao ha import nem bundler de proposito: o codigo que o eleitor precisa auditar deve caber
// numa leitura, e chegar ao navegador exatamente como esta no repositorio.

/** Tamanho de um hash SHA-256, em bytes. */
export const HASH_SIZE = 32;

// Prefixos de dominio da RFC 6962. Sem eles, uma folha pode ser forjada para se passar por
// um no interno (segunda pre-imagem). O 0x02 e da cadeia de checkpoints, pelo mesmo motivo.
const PREFIXO_FOLHA = 0x00;
const PREFIXO_NO = 0x01;
const PREFIXO_ELO = 0x02;

/** O antecessor do primeiro checkpoint: 32 bytes zerados. */
export const GENESE = new Uint8Array(HASH_SIZE);

/** Converte hexadecimal em bytes. Lanca se a string nao for hexadecimal par. */
export function hexParaBytes(hex) {
  if (typeof hex !== "string" || hex.length % 2 !== 0 || !/^[0-9a-fA-F]*$/.test(hex)) {
    throw new Error(`nao e hexadecimal: ${hex}`);
  }
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

/** Converte bytes em hexadecimal minusculo. */
export function bytesParaHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Compara dois arrays de bytes em tempo constante no comprimento. */
export function bytesIguais(a, b) {
  if (a.length !== b.length) return false;
  let diferenca = 0;
  for (let i = 0; i < a.length; i++) diferenca |= a[i] ^ b[i];
  return diferenca === 0;
}

function concatenar(partes) {
  const total = partes.reduce((soma, p) => soma + p.length, 0);
  const saida = new Uint8Array(total);
  let off = 0;
  for (const parte of partes) {
    saida.set(parte, off);
    off += parte.length;
  }
  return saida;
}

async function sha256(...partes) {
  const digest = await crypto.subtle.digest("SHA-256", concatenar(partes));
  return new Uint8Array(digest);
}

/** hash de folha: SHA-256(0x00 || dados). */
export function hashFolha(dados) {
  return sha256(Uint8Array.of(PREFIXO_FOLHA), dados);
}

/** hash de no interno: SHA-256(0x01 || esquerda || direita). */
export function hashNo(esquerda, direita) {
  return sha256(Uint8Array.of(PREFIXO_NO), esquerda, direita);
}

/**
 * hash de um elo da cadeia: SHA-256(0x02 || anterior || raiz || windowId || "|" || tamanho).
 *
 * windowId e tamanho entram junto com a raiz porque, sem eles, duas janelas distintas com o
 * mesmo conjunto de folhas produziriam elos identicos.
 */
export function hashElo(anterior, raiz, windowId, tamanho) {
  const utf8 = new TextEncoder();
  return sha256(
    Uint8Array.of(PREFIXO_ELO),
    anterior,
    raiz,
    utf8.encode(windowId),
    utf8.encode(`|${tamanho}`),
  );
}

/**
 * Confere que `folha` esta na posicao `indice` de uma arvore de `tamanho` folhas cuja raiz
 * e `raiz`.
 *
 * Sobe da folha ate a raiz mantendo o indice do no (fn) e o indice do ultimo no daquele
 * nivel (sn). Comparar os dois e o que revela quando o no esta na borda direita da arvore,
 * o caso em que a subarvore nao esta cheia.
 *
 * Os testes de fn/sn tambem sao a defesa contra provas de tamanho errado: um caminho mais
 * longo que a arvore esgota sn no meio do percurso, e um mais curto termina com sn != 0.
 */
export async function verificarProva(raiz, folha, { indice, tamanho, caminho }) {
  if (!Number.isInteger(indice) || !Number.isInteger(tamanho)) return false;
  if (indice < 0 || tamanho <= 0 || indice >= tamanho) return false;

  let calculado = await hashFolha(folha);
  let fn = indice;
  let sn = tamanho - 1;

  for (const irmao of caminho) {
    if (sn === 0) return false; // caminho mais longo que a arvore
    if (irmao.length !== HASH_SIZE) return false;

    if (fn % 2 === 1 || fn === sn) {
      calculado = await hashNo(irmao, calculado);
      while (fn !== 0 && fn % 2 === 0) {
        fn >>= 1;
        sn >>= 1;
      }
    } else {
      calculado = await hashNo(calculado, irmao);
    }
    fn >>= 1;
    sn >>= 1;
  }

  return sn === 0 && bytesIguais(calculado, raiz);
}

/**
 * Refaz os elos da cadeia de raizes e diz onde ela quebra, se quebrar.
 *
 * Reescrever uma janela antiga muda todos os elos seguintes: conferir a cadeia inteira e o
 * que transforma "esta raiz e coerente" em "esta historia nao foi reescrita".
 *
 * Devolve { ok, ate, falha }: `ate` e quantos elos fecharam antes do problema.
 */
export async function verificarCadeia(checkpoints) {
  let anterior = GENESE;

  for (let i = 0; i < checkpoints.length; i++) {
    const ponto = checkpoints[i];
    const raiz = hexParaBytes(ponto.root);
    const declaradoAnterior = hexParaBytes(ponto.previousHash);
    const declaradoElo = hexParaBytes(ponto.checkpointHash);

    if (ponto.sequence !== i) {
      return { ok: false, ate: i, falha: `janela ${ponto.windowId}: sequencia ${ponto.sequence} fora de ordem` };
    }
    if (!bytesIguais(declaradoAnterior, anterior)) {
      return { ok: false, ate: i, falha: `janela ${ponto.windowId}: nao se encadeia na anterior` };
    }
    const esperado = await hashElo(anterior, raiz, ponto.windowId, ponto.leafCount);
    if (!bytesIguais(esperado, declaradoElo)) {
      return { ok: false, ate: i, falha: `janela ${ponto.windowId}: o elo publicado nao confere com o recalculado` };
    }
    anterior = declaradoElo;
  }

  return { ok: true, ate: checkpoints.length, falha: null };
}
