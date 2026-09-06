// Liga a interface ao servico de provas.
//
// A divisao com verify.js e proposital: aqui mora tudo que depende de rede e de DOM, la mora
// so a aritmetica de hashes. Quem quiser auditar a verificacao le um arquivo so, sem passar
// por fetch, evento de clique ou formatacao de data.

import {
  bytesParaHex,
  hexParaBytes,
  hashFolha,
  verificarCadeia,
  verificarProva,
} from "./verify.js";

// Mesma origem: o nginx que serve esta pagina encaminha /api para o servico Go. Sem isso
// seria preciso CORS, e a pagina passaria a depender de uma configuracao do servidor para
// funcionar - exatamente o tipo de acoplamento que este frontend existe para nao ter.
const API = "/api";

const RECIBO_VALIDO = /^[0-9a-f]{64}$/;

const formulario = document.getElementById("formulario");
const campoRecibo = document.getElementById("recibo");
const botaoConferir = document.getElementById("conferir");
const painelResultado = document.getElementById("resultado");
const botaoCadeia = document.getElementById("conferir-cadeia");
const painelCadeia = document.getElementById("cadeia");

formulario.addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const recibo = campoRecibo.value.trim().toLowerCase();

  if (!RECIBO_VALIDO.test(recibo)) {
    mostrar(painelResultado, cartaoErro(
      "Isso não parece um recibo",
      "Um recibo tem 64 caracteres hexadecimais (0-9, a-f). Confira se o texto foi colado inteiro.",
    ));
    return;
  }

  // O que fica na tela tem de ser o que foi conferido: se o eleitor colou em maiusculas,
  // o campo passa a mostrar a forma normalizada que entrou na conta.
  campoRecibo.value = recibo;

  ocupado(botaoConferir, true);
  try {
    await conferirRecibo(recibo);
  } catch (erro) {
    mostrar(painelResultado, cartaoErro("Não consegui falar com o serviço", String(erro.message ?? erro)));
  } finally {
    ocupado(botaoConferir, false);
  }
});

botaoCadeia.addEventListener("click", async () => {
  ocupado(botaoCadeia, true);
  try {
    await conferirCadeia();
  } catch (erro) {
    painelCadeia.replaceChildren(cartaoErro("Não consegui falar com o serviço", String(erro.message ?? erro)));
  } finally {
    ocupado(botaoCadeia, false);
  }
});

async function conferirRecibo(recibo) {
  const resposta = await fetch(`${API}/proof/${recibo}`);

  if (resposta.status === 404) {
    const corpo = await resposta.json().catch(() => ({}));
    // 404 aqui nao quer dizer "voto invalido", e a tela nao pode deixar o eleitor concluir
    // a pior das tres possibilidades sem que ela seja a unica.
    mostrar(painelResultado, cartaoIndefinido(corpo.message));
    return;
  }
  if (!resposta.ok) {
    throw new Error(`o serviço respondeu ${resposta.status}`);
  }

  const prova = await resposta.json();

  // O passo que importa: a partir daqui nada do que o servidor disse e aceito de graca.
  const folha = hexParaBytes(prova.receipt);
  const raiz = hexParaBytes(prova.root);
  const confere = await verificarProva(raiz, folha, {
    indice: prova.leafIndex,
    tamanho: prova.treeSize,
    caminho: prova.path.map(hexParaBytes),
  });

  const hashDaFolha = bytesParaHex(await hashFolha(folha));
  mostrar(painelResultado, confere ? cartaoAceito(prova, hashDaFolha) : cartaoRejeitado(prova));
}

async function conferirCadeia() {
  const resposta = await fetch(`${API}/roots`);
  if (!resposta.ok) throw new Error(`o serviço respondeu ${resposta.status}`);

  const checkpoints = await resposta.json();
  if (checkpoints.length === 0) {
    painelCadeia.replaceChildren(aviso("Nenhuma janela foi selada ainda."));
    return;
  }

  const resultado = await verificarCadeia(checkpoints);
  painelCadeia.replaceChildren(cartaoCadeia(checkpoints, resultado));
}

// --- montagem dos cartoes -------------------------------------------------------------

function cartaoAceito(prova, hashDaFolha) {
  const fragmento = document.createDocumentFragment();
  fragmento.append(
    veredito("ok", "Seu voto está na apuração", [
      "A prova fecha contra a raiz da janela. Ela foi recalculada aqui, no seu navegador.",
    ]),
    detalhes(prova, hashDaFolha),
    ressalva(prova),
  );
  return fragmento;
}

function cartaoRejeitado(prova) {
  const fragmento = document.createDocumentFragment();
  fragmento.append(
    veredito("falha", "A prova não fecha", [
      "O serviço devolveu uma prova que não reproduz a raiz que ele mesmo publicou.",
      "Isso não é um voto ausente: é uma resposta inconsistente, e deve ser reportada.",
    ]),
    detalhes(prova, null),
  );
  return fragmento;
}

function cartaoIndefinido(mensagem) {
  return veredito("pendente", "Ainda não há prova para este recibo", [
    mensagem ??
      "O recibo não está em nenhuma janela selada.",
    "Três explicações cabem aqui, e a tela não tem como distinguir: a janela pode ainda estar aberta, o voto pode ter sido recusado por duplicidade, ou o recibo pode não existir.",
  ]);
}

function cartaoErro(titulo, mensagem) {
  return veredito("falha", titulo, [mensagem]);
}

function veredito(tipo, titulo, paragrafos) {
  const bloco = document.createElement("div");
  bloco.className = `veredito ${tipo}`;

  const h = document.createElement("h2");
  h.textContent = titulo;
  bloco.append(h);

  for (const texto of paragrafos) {
    const p = document.createElement("p");
    p.textContent = texto;
    bloco.append(p);
  }
  return bloco;
}

function detalhes(prova, hashDaFolha) {
  const linhas = [
    ["Janela", prova.windowId],
    ["Posição na apuração", `folha ${prova.leafIndex + 1} de ${prova.treeSize}`],
    ["Selada em", formatarData(prova.sealedAt)],
    ["Passos da prova", `${prova.path.length} ${prova.path.length === 1 ? "hash" : "hashes"}`],
  ];
  if (hashDaFolha) linhas.push(["Hash da sua folha", hashDaFolha]);
  linhas.push(
    ["Raiz da janela", prova.root],
    ["Elo anterior", prova.previousHash],
    ["Hash do checkpoint", prova.checkpointHash],
  );

  const tabela = document.createElement("dl");
  tabela.className = "detalhes";
  for (const [rotulo, valor] of linhas) {
    const dt = document.createElement("dt");
    dt.textContent = rotulo;
    const dd = document.createElement("dd");
    if (/^[0-9a-f]{64}$/.test(valor)) {
      dd.className = "hash";
      dd.append(hashCopiavel(valor));
    } else {
      dd.textContent = valor;
    }
    tabela.append(dt, dd);
  }
  return tabela;
}

// A ressalva mais importante da tela, e a razao de ela nao terminar num "verificado" verde.
function ressalva(prova) {
  const bloco = document.createElement("details");
  bloco.className = "ressalva";

  const resumo = document.createElement("summary");
  resumo.textContent = "O que isto prova, e o que não prova";
  bloco.append(resumo);

  const p1 = document.createElement("p");
  p1.textContent =
    "A conta acima prova que o seu recibo está na árvore cuja raiz aparece aqui. Ela não prova, sozinha, que essa é a raiz que a apuração publicou: a prova e a raiz vieram do mesmo servidor.";
  const p2 = document.createElement("p");
  p2.textContent =
    `Para fechar o círculo, compare o hash do checkpoint acima com o que foi publicado no tópico merkle.roots para a janela ${prova.windowId} — ou com o que outro observador da eleição leu. Duas fontes independentes com o mesmo elo é o que torna a raiz um compromisso público.`;
  const p3 = document.createElement("p");
  p3.textContent =
    "Conferir a cadeia inteira, no bloco abaixo, é o outro lado disso: mostra que nenhuma janela anterior foi reescrita depois de publicada.";

  bloco.append(p1, p2, p3);
  return bloco;
}

function cartaoCadeia(checkpoints, resultado) {
  const fragmento = document.createDocumentFragment();
  const total = checkpoints.length;
  const folhas = checkpoints.reduce((soma, c) => soma + c.leafCount, 0);

  fragmento.append(
    resultado.ok
      ? veredito("ok", `${total} ${total === 1 ? "janela" : "janelas"}, ${folhas} votos, cadeia íntegra`, [
          "Todos os elos foram recalculados neste navegador, do genesis até a última raiz.",
        ])
      : veredito("falha", "A cadeia não fecha", [
          resultado.falha,
          `Os ${resultado.ate} primeiros elos conferem; o problema está no seguinte.`,
        ]),
  );

  const lista = document.createElement("ol");
  lista.className = "cadeia";
  checkpoints.forEach((ponto, i) => {
    const item = document.createElement("li");
    if (!resultado.ok && i === resultado.ate) item.className = "quebrado";

    const titulo = document.createElement("div");
    titulo.className = "janela";
    titulo.textContent = `${ponto.windowId} · ${ponto.leafCount} ${ponto.leafCount === 1 ? "voto" : "votos"}`;

    const elo = document.createElement("div");
    elo.className = "hash";
    elo.append(hashCopiavel(ponto.checkpointHash));

    item.append(titulo, elo);
    lista.append(item);
  });
  fragmento.append(lista);
  return fragmento;
}

function hashCopiavel(valor) {
  const botao = document.createElement("button");
  botao.type = "button";
  botao.className = "copiar";
  botao.title = "Copiar";
  botao.textContent = valor;
  botao.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(valor);
      const antes = botao.textContent;
      botao.textContent = "copiado";
      botao.classList.add("copiado");
      setTimeout(() => {
        botao.textContent = antes;
        botao.classList.remove("copiado");
      }, 1200);
    } catch {
      // Sem permissao de area de transferencia: o valor continua visivel e selecionavel.
    }
  });
  return botao;
}

function aviso(texto) {
  const p = document.createElement("p");
  p.className = "ajuda";
  p.textContent = texto;
  return p;
}

function formatarData(iso) {
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? iso : data.toLocaleString("pt-BR");
}

function mostrar(painel, conteudo) {
  painel.replaceChildren(conteudo);
  painel.hidden = false;
}

function ocupado(botao, estado) {
  botao.disabled = estado;
  botao.dataset.ocupado = String(estado);
}
