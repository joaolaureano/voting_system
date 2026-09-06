// Testes do verificador do navegador, rodados por `node --test` - sem npm, sem dependencia.
//
// Os vetores vem de pkg/checkpoint, gerados por `go run ./cmd/vetores`. E de proposito: um
// verificador que so concordasse consigo mesmo nao provaria nada. O que estes testes
// perguntam e se a implementacao em JavaScript chega aos mesmos hashes que a de Go.

import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

import {
  GENESE,
  bytesIguais,
  bytesParaHex,
  hashElo,
  hashFolha,
  hashNo,
  hexParaBytes,
  verificarCadeia,
  verificarProva,
} from "../public/verify.js";

const vetores = JSON.parse(
  await readFile(new URL("./vetores.json", import.meta.url), "utf8"),
);

/** Monta os argumentos de verificarProva a partir de uma prova do vetor. */
function prova(p) {
  return {
    indice: p.leafIndex,
    tamanho: p.treeSize,
    caminho: p.path.map(hexParaBytes),
  };
}

test("hexadecimal ida e volta", () => {
  const hex = "00ff10abcdef";
  assert.equal(bytesParaHex(hexParaBytes(hex)), hex);
  assert.throws(() => hexParaBytes("xyz"), /nao e hexadecimal/);
  assert.throws(() => hexParaBytes("abc"), /nao e hexadecimal/);
});

test("os prefixos de dominio separam folha de no interno", async () => {
  const dados = hexParaBytes("00".repeat(32));
  const folha = await hashFolha(dados);
  const no = await hashNo(dados, dados);
  assert.ok(!bytesIguais(folha, no), "folha e no nao podem colidir");
});

test("toda prova gerada pelo Go e aceita", async () => {
  let conferidas = 0;
  for (const janela of vetores.janelas) {
    const raiz = hexParaBytes(janela.root);
    for (const p of janela.provas) {
      const folha = hexParaBytes(p.receipt);
      assert.ok(
        await verificarProva(raiz, folha, prova(p)),
        `janela ${janela.windowId}, folha ${p.leafIndex}/${p.treeSize} deveria ser aceita`,
      );
      conferidas++;
    }
  }
  assert.ok(conferidas >= 25, `esperava conferir os 25 vetores, conferi ${conferidas}`);
});

// Os tres ataques que a verificacao existe para barrar. Se qualquer um passar, o frontend
// esta dando ao eleitor uma garantia que ele nao tem.

test("folha forjada e rejeitada", async () => {
  for (const janela of vetores.janelas) {
    const raiz = hexParaBytes(janela.root);
    const p = janela.provas[0];
    const forjada = hexParaBytes(p.receipt);
    forjada[0] ^= 0xff;
    assert.ok(
      !(await verificarProva(raiz, forjada, prova(p))),
      `janela ${janela.windowId} aceitou uma folha que nao esta na arvore`,
    );
  }
});

test("caminho adulterado e rejeitado", async () => {
  for (const janela of vetores.janelas) {
    const raiz = hexParaBytes(janela.root);
    const p = janela.provas.find((x) => x.path.length > 0);
    if (!p) continue; // arvore de uma folha so nao tem caminho
    const adulterada = prova(p);
    adulterada.caminho[0] = hexParaBytes(p.path[0]);
    adulterada.caminho[0][0] ^= 0x01;
    assert.ok(
      !(await verificarProva(raiz, hexParaBytes(p.receipt), adulterada)),
      `janela ${janela.windowId} aceitou um caminho adulterado`,
    );
  }
});

test("raiz de outra janela e rejeitada", async () => {
  const [primeira, segunda] = vetores.janelas;
  const p = segunda.provas[0];
  assert.ok(
    !(await verificarProva(hexParaBytes(primeira.root), hexParaBytes(p.receipt), prova(p))),
    "a prova de uma janela nao pode fechar contra a raiz de outra",
  );
});

// Provas de tamanho errado sao o caso que separa uma verificacao correta de uma que so
// parece correta: e o teste de fn/sn, e nao o comprimento do caminho, que as barra.

test("caminho mais longo que a arvore e rejeitado", async () => {
  const janela = vetores.janelas.find((j) => j.leafCount === 3);
  const p = janela.provas[0];
  const inflada = prova(p);
  inflada.caminho.push(hexParaBytes("11".repeat(32)));
  assert.ok(!(await verificarProva(hexParaBytes(janela.root), hexParaBytes(p.receipt), inflada)));
});

test("caminho mais curto que a arvore e rejeitado", async () => {
  const janela = vetores.janelas.find((j) => j.leafCount === 12);
  const p = janela.provas[0];
  const cortada = prova(p);
  cortada.caminho.pop();
  assert.ok(!(await verificarProva(hexParaBytes(janela.root), hexParaBytes(p.receipt), cortada)));
});

test("indice fora da arvore e rejeitado", async () => {
  const janela = vetores.janelas.find((j) => j.leafCount === 7);
  const p = janela.provas[0];
  for (const indice of [-1, 7, 99]) {
    assert.ok(
      !(await verificarProva(hexParaBytes(janela.root), hexParaBytes(p.receipt), { ...prova(p), indice })),
      `indice ${indice} deveria ser rejeitado`,
    );
  }
});

test("o elo recalculado bate com o que o Go publicou", async () => {
  let anterior = GENESE;
  for (const janela of vetores.janelas) {
    assert.equal(bytesParaHex(anterior), janela.previousHash, `elo anterior de ${janela.windowId}`);
    const elo = await hashElo(anterior, hexParaBytes(janela.root), janela.windowId, janela.leafCount);
    assert.equal(bytesParaHex(elo), janela.checkpointHash, `elo de ${janela.windowId}`);
    anterior = elo;
  }
});

test("a cadeia inteira fecha", async () => {
  const resultado = await verificarCadeia(vetores.janelas);
  assert.ok(resultado.ok, resultado.falha);
  assert.equal(resultado.ate, vetores.janelas.length);
});

// Reescrever uma janela antiga muda todos os elos seguintes: e essa propriedade que faz a
// ultima raiz publicada comprometer a historia inteira.

test("uma janela reescrita quebra a cadeia no ponto certo", async () => {
  const adulterada = structuredClone(vetores.janelas);
  const alvo = 1;
  adulterada[alvo].root = "ff".repeat(32);

  const resultado = await verificarCadeia(adulterada);
  assert.ok(!resultado.ok, "a cadeia aceitou uma raiz trocada");
  assert.equal(resultado.ate, alvo, "a quebra tem de apontar a janela reescrita");
});

test("uma janela removida do meio quebra a cadeia", async () => {
  const semUma = vetores.janelas.filter((_, i) => i !== 2);
  const resultado = await verificarCadeia(semUma);
  assert.ok(!resultado.ok, "a cadeia aceitou um buraco");
});

test("a contagem de folhas entra no elo", async () => {
  const janela = vetores.janelas[0];
  const raiz = hexParaBytes(janela.root);
  const certo = await hashElo(GENESE, raiz, janela.windowId, janela.leafCount);
  const errado = await hashElo(GENESE, raiz, janela.windowId, janela.leafCount + 1);
  assert.ok(!bytesIguais(certo, errado), "o tamanho tem de fazer diferenca no elo");
});

test("o windowId entra no elo", async () => {
  const janela = vetores.janelas[0];
  const raiz = hexParaBytes(janela.root);
  const certo = await hashElo(GENESE, raiz, janela.windowId, janela.leafCount);
  const errado = await hashElo(GENESE, raiz, "outra-janela", janela.leafCount);
  assert.ok(!bytesIguais(certo, errado), "o windowId tem de fazer diferenca no elo");
});
