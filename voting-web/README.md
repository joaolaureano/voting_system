# voting-web

A tela onde o eleitor confere o comprovante — e o ponto todo é que **a verificação roda no
navegador dele**, não no servidor.

O serviço Go entrega o recibo, a prova de inclusão e a raiz da janela. Quem refaz a conta da
RFC 6962 e decide se ela fecha é [`public/verify.js`](public/verify.js), na máquina de quem
perguntou. Um servidor que quisesse mentir teria de forjar SHA-256.

## Rodar

Sobe junto com o resto da stack, em <http://localhost:8084>:

```bash
make up
```

## Testes

```bash
make test-web     # ou: cd voting-web && npm test
```

Sem `npm install`: não há dependência nenhuma. `node --test` e a WebCrypto do próprio Node dão
conta.

Os testes **não** conferem o verificador contra ele mesmo. Os vetores em
[`test/vetores.json`](test/vetores.json) são gerados por `pkg/checkpoint`, a implementação em
Go:

```bash
make web-vetores   # cd voting-merkle && go run ./cmd/vetores > ../voting-web/test/vetores.json
```

Uma segunda implementação do mesmo algoritmo só tem valor se for confrontada com a primeira.
Além das provas válidas, os testes cobrem os casos que a verificação existe para barrar: folha
forjada, caminho adulterado, raiz de outra janela, caminho mais longo e mais curto que a
árvore, índice fora dela, e uma janela reescrita no meio da cadeia.

### No navegador de verdade

Os testes acima cobrem a aritmética. O que só existe no navegador — `fetch`, DOM, os vereditos
que o eleitor lê — tem sua própria verificação, que sobe um stub com o contrato de `/proof` e
`/roots` e dirige o Chrome pela página:

```bash
npm run test:browser   # exige Playwright e um Chrome instalado
```

Fica fora de `npm test` de propósito: ela precisa de dependência, e a promessa de que o pacote
não tem nenhuma vale para os testes do dia a dia.

São 14 verificações, e a que justifica o arquivo inteiro é a quinta: **o stub devolve uma prova
adulterada, e a tela tem de rejeitá-la**. É a diferença entre "o servidor disse que está tudo
bem" e "eu conferi". As outras cobrem a árvore de uma folha só, o recibo sem prova, a entrada
malformada, a cadeia íntegra e uma janela reescrita no meio dela.

## Estrutura

| Arquivo | Papel |
|---|---|
| `public/verify.js` | a aritmética de hashes. Sem DOM, sem `fetch`, sem dependência. |
| `public/app.js` | rede e interface. Tudo que depende do navegador. |
| `public/index.html`, `public/styles.css` | a página. |
| `nginx.conf` | serve os estáticos e encaminha `/api` para o serviço Go. |
| `test/verify_test.js` | a aritmética, contra os vetores do Go. Sem dependência. |
| `test/browser/check.mjs` | a página num Chrome real, contra um stub do contrato da API. |

A divisão entre `verify.js` e `app.js` é deliberada: quem for auditar a verificação lê um
arquivo só, e ele é o mesmo que os testes importam.

## O que a tela diz, e o que ela se recusa a dizer

**Recibo selado.** A prova fecha contra a raiz. A tela mostra a conta e, num bloco à parte, a
ressalva que ela não esconde: prova e raiz vieram do mesmo servidor, então isso ainda não é o
mesmo que "esta é a raiz que a apuração publicou". Fechar o círculo é comparar o hash do
checkpoint com o publicado em `merkle.roots`, ou com o que outro observador leu.

**Recibo sem prova.** Três explicações cabem — janela ainda aberta, voto recusado por
duplicidade, recibo inexistente — e a tela não tem como distinguir. Ela diz as três, em vez de
deixar o eleitor concluir a pior.

**A cadeia.** O botão refaz todos os elos, do genesis à última raiz, e aponta a janela exata
onde a cadeia quebraria. É o que mostra que nenhuma janela antiga foi reescrita depois de
publicada.

## Sem build, sem framework, sem CDN

Nada é buscado de fora da origem: nem fonte, nem script, nem folha de estilo. A página inteira
são quatro arquivos servidos por nginx. Isso não é minimalismo por gosto — é o que permite
afirmar que o código auditado é o código executado.
