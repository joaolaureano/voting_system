// Verificacao da pagina num navegador de verdade.
//
// Os testes de test/verify_test.js cobrem a aritmetica; este cobre o que so existe no
// navegador: o fetch, o DOM e os vereditos que a tela mostra ao eleitor. Sobe um stub com o
// MESMO contrato do servico Go (ProofResponse e RootResponse) e dirige o Chrome pela pagina.
//
// Fica fora de `npm test` de proposito: exige Playwright e um Chrome instalado, e a promessa
// de que o pacote nao tem dependencia vale para os testes do dia a dia.
//
//   cd voting-web && npx playwright@1.63.0 --help >/dev/null && npm run test:browser
//
// O caso que justifica o arquivo inteiro e o quinto: o stub devolve uma prova adulterada, e
// a tela tem de rejeita-la. E a diferenca entre "o servidor disse que esta tudo bem" e "eu
// conferi".

import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const PORTA = 4173;

const vetores = JSON.parse(await readFile(path.join(RAIZ, "test/vetores.json"), "utf8"));

// Monta as respostas com os campos exatos da API, a partir dos vetores que o Go gerou.
const provas = new Map();
const roots = vetores.janelas.map((j) => ({
  windowId: j.windowId,
  sequence: j.sequence,
  leafCount: j.leafCount,
  root: j.root,
  previousHash: j.previousHash,
  checkpointHash: j.checkpointHash,
  sealedAt: "2026-01-01T00:00:30Z",
}));

for (const j of vetores.janelas) {
  for (const p of j.provas) {
    provas.set(p.receipt, {
      receipt: p.receipt,
      windowId: j.windowId,
      sequence: j.sequence,
      leafIndex: p.leafIndex,
      treeSize: p.treeSize,
      root: j.root,
      previousHash: j.previousHash,
      checkpointHash: j.checkpointHash,
      sealedAt: "2026-01-01T00:00:30Z",
      path: p.path,
      verification: "RFC 6962: folha = SHA-256(0x00 || bytes(receipt))",
    });
  }
}

// O stub obedece a estes dois interruptores para encenar um servidor desonesto.
const stub = { forjarProva: false, roots };

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
};

const servidor = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://stub");
  const json = (status, corpo) => {
    res.writeHead(status, { "content-type": "application/json" });
    res.end(JSON.stringify(corpo));
  };

  if (url.pathname === "/api/roots") return json(200, stub.roots);

  if (url.pathname.startsWith("/api/proof/")) {
    const original = provas.get(url.pathname.slice("/api/proof/".length));
    if (!original) {
      return json(404, {
        error: "RECIBO_NAO_SELADO",
        message:
          "recibo nao esta em nenhuma janela selada; a janela pode ainda estar aberta, " +
          "ou o voto foi recusado por duplicidade",
      });
    }
    if (!stub.forjarProva) return json(200, original);

    const adulterada = { ...original, path: [...original.path] };
    if (adulterada.path.length) adulterada.path[0] = "ff".repeat(32);
    return json(200, adulterada);
  }

  const arquivo = url.pathname === "/" ? "/index.html" : url.pathname;
  try {
    const corpo = await readFile(path.join(RAIZ, "public", arquivo));
    res.writeHead(200, { "content-type": MIME[path.extname(arquivo)] ?? "application/octet-stream" });
    res.end(corpo);
  } catch {
    res.writeHead(404).end("nao achei");
  }
});

await new Promise((pronto) => servidor.listen(PORTA, pronto));

const navegador = await chromium.launch({ channel: "chrome" });
const pagina = await navegador.newPage({ viewport: { width: 1000, height: 1100 } });

const erros = [];
pagina.on("pageerror", (e) => erros.push(`erro de script: ${e}`));
// O 404 do recibo inexistente e esperado; qualquer outro nao e.
pagina.on("response", (r) => {
  if (r.status() >= 400 && !r.url().includes("/api/proof/")) erros.push(`${r.status()} em ${r.url()}`);
});

const casos = [];
const conferir = (nome, ok, detalhe = "") => casos.push({ nome, ok, detalhe });

/** Espera o veredito MUDAR, e nao apenas existir: o painel anterior continua na tela. */
async function vereditoVira(trecho, painel = "#resultado") {
  try {
    await pagina.waitForFunction(
      ([sel, t]) => document.querySelector(`${sel} .veredito h2`)?.textContent.includes(t),
      [painel, trecho],
      { timeout: 5000 },
    );
    return true;
  } catch {
    return false;
  }
}

async function conferirRecibo(recibo) {
  await pagina.fill("#recibo", recibo);
  await pagina.click("#conferir");
}

await pagina.goto(`http://localhost:${PORTA}/`);

// 1) Recibo valido na arvore de 12 folhas - a que tem o caminho mais longo.
const janela12 = vetores.janelas.find((j) => j.leafCount === 12);
const alvo = janela12.provas[5].receipt;

await conferirRecibo(alvo.toUpperCase()); // maiusculas: a tela normaliza
conferir("recibo selado e aceito", await vereditoVira("está na apuração"));
conferir("normaliza o recibo na tela", (await pagina.inputValue("#recibo")) === alvo);
conferir(
  "mostra a posicao na apuracao",
  (await pagina.textContent("#resultado .detalhes")).includes("folha 6 de 12"),
);
await pagina.click("#resultado .ressalva summary");
conferir(
  "a ressalva sobre a origem da raiz esta na tela",
  (await pagina.textContent("#resultado .ressalva")).includes("merkle.roots"),
);

// 2) Arvore de uma folha so: caminho vazio, o caso de borda.
const janela1 = vetores.janelas.find((j) => j.leafCount === 1);
await conferirRecibo(janela1.provas[0].receipt);
conferir("arvore de uma folha so e aceita", await vereditoVira("está na apuração"));

// 3) Recibo sem prova: as tres explicacoes, e nao "voto invalido".
await conferirRecibo("ab".repeat(32));
conferir("recibo sem prova nao vira acusacao", await vereditoVira("Ainda não há prova"));
const pendente = await pagina.textContent("#resultado .veredito");
conferir("diz as tres explicacoes", pendente.includes("duplicidade") && pendente.includes("aberta"));

// 4) Entrada malformada nem chega na rede.
await conferirRecibo("nao-e-um-hash");
conferir("entrada malformada e barrada na tela", await vereditoVira("não parece um recibo"));

// 5) O servidor mente: prova adulterada tem de ser rejeitada no navegador.
stub.forjarProva = true;
await conferirRecibo(alvo);
conferir("prova adulterada pelo servidor e rejeitada", await vereditoVira("não fecha"));
stub.forjarProva = false;

// 6) A cadeia inteira, recalculada no navegador.
await pagina.click("#conferir-cadeia");
conferir("a cadeia integra e aceita", await vereditoVira("cadeia íntegra", "#cadeia"));
const resumo = await pagina.textContent("#cadeia .veredito h2");
conferir("conta janelas e votos", resumo.includes("5 janelas") && resumo.includes("25 votos"), resumo);

// 7) Uma janela reescrita: a cadeia quebra, e a tela aponta qual.
stub.roots = roots.map((r, i) => (i === 2 ? { ...r, root: "ff".repeat(32) } : r));
await pagina.click("#conferir-cadeia");
conferir("cadeia reescrita e rejeitada", await vereditoVira("não fecha", "#cadeia"));
const quebrados = await pagina.$$("#cadeia li.quebrado");
conferir("aponta exatamente uma janela", quebrados.length === 1);
if (quebrados.length === 1) {
  const texto = await quebrados[0].textContent();
  conferir("e a janela reescrita", texto.includes(roots[2].windowId), roots[2].windowId);
}

await navegador.close();
servidor.close();

let falhou = false;
for (const c of casos) {
  if (!c.ok) falhou = true;
  console.log(`${c.ok ? "ok  " : "FALHA"}  ${c.nome}${c.detalhe ? `  [${c.detalhe}]` : ""}`);
}
for (const e of erros) {
  falhou = true;
  console.log(`FALHA  ${e}`);
}
console.log(falhou ? "\nfalhou" : `\n${casos.length} verificacoes no navegador, zero erro de console`);
process.exit(falhou ? 1 : 0);
