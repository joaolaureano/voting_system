# Sistema de Votação em Tempo Real — Kafka + Flink

Apuração de votos em streaming: os votos entram por uma API REST, vão para o Kafka e um job
Flink garante **um voto por eleitor** e mantém a contagem corrente por **candidato, estado,
cidade e partido**.

```
POST /api/v1/votes ──► ingest-api ──► [votes.cast] ──► Flink ──┬─► [results.by-candidate]
                            │                            │     ├─► [results.by-state]
                            │                          dedup   ├─► [results.by-city]
                            └──► [votes.receipts]         │     ├─► [results.by-party]
                                                          │     └─► [votes.rejected]
                                                          │
                                    [votes.accepted] ◄────┤
                                    [votes.windows]  ◄────┘
                                           │
                                           ▼
                                    merkle-service (Go) ──► [merkle.roots]
                                           │
                                           └──► GET /proof/{recibo}
```

## Índice

[Como rodar](#como-rodar) · [Arquitetura](#arquitetura) · [Tópicos](#tópicos) ·
[Teste de carga](#teste-de-carga) · [Merkle Tree](#merkle-tree-e-prova-de-inclusão) ·
[Garantias](#garantias) · [Privacidade do recibo](#nota-de-privacidade-sobre-o-recibo) ·
[Encerramento](#encerramento-da-votação) ·
[Marca d'água](#o-travamento-da-marca-dágua-e-por-que-heartbeats) ·
[Próximas fases](#próximas-fases)

## Como rodar

Requisitos: Docker, um JDK 17+ e Go 1.24+. O `Makefile` usa `/opt/homebrew/opt/openjdk@21`
por padrão — sobrescreva com `make JAVA_HOME=...`.

```bash
make test        # 120 testes (84 Java + 36 Go)
make up          # Kafka (KRaft), Flink, API de ingestão, serviço Merkle e kafka-ui
make submit      # submete o job de apuração
make bench       # teste de carga com Gatling (10.000 votos, 10% duplicatas)
make results     # placar corrente por candidato e por estado
make rejected    # votos recusados
make roots       # cadeia de raízes Merkle já seladas
make proof RECEIPT=<hash>   # prova de inclusão de um recibo
make down        # derruba tudo e apaga os dados
```

| Serviço | Endereço |
|---|---|
| API de ingestão | http://localhost:8081 |
| Interface do Flink | http://localhost:8082 |
| Serviço Merkle (provas) | http://localhost:8083 |
| Kafka UI | http://localhost:8080 |
| Kafka (do host) | `localhost:29092` |

### Votar

```bash
curl -XPOST localhost:8081/api/v1/votes -H 'Content-Type: application/json' -d '{
  "voterId": "voter-1", "candidateId": "cand-1", "partyId": "PARTIDO-A",
  "state": "sp", "city": "Sao Paulo"
}'
# 201 {"receipt":"38641edb…","status":"ACCEPTED","castAt":"2026-09-06T07:40:22.299Z"}
```

`ACCEPTED` significa **aceito para apuração**, não "computado". A unicidade é decidida
adiante, pelo Flink, que é quem tem o estado de todos os eleitores. O recibo é o que permite
conferir o desfecho depois.

## Arquitetura

Módulos Maven, do centro para a borda. A dependência só aponta para dentro:

| Módulo | Papel |
|---|---|
| `voting-domain` | **Java puro.** Voto, unicidade, recibo, apuração. Sem Kafka, Flink, Spring ou Jackson. |
| `voting-application` | Casos de uso e portas. Depende só do domínio. |
| `voting-contracts` | Eventos que trafegam no Kafka + tradução de/para o domínio. |
| `voting-ingest-api` | Adaptadores: REST de entrada, produtor Kafka de saída. |
| `voting-streaming` | Adaptador Flink: fontes, sinks e funções finas que delegam ao domínio. |
| `voting-benchmark` | Teste de carga com Gatling sobre uma base fixa de partidos, candidatos e municípios. |
| `voting-merkle` | **Go.** Sela cada janela numa árvore de Merkle encadeada e serve provas de inclusão. |

A regra que sustenta o desacoplamento: **`voting-domain/pom.xml` não declara nenhuma
dependência externa** (só JUnit em teste). Se algo de infraestrutura precisar entrar ali, é
sinal de que a modelagem vazou.

Duas consequências concretas disso no código:

- `TallyDimension` (domínio) sabe extrair sua própria chave de um voto. Por isso o job Flink
  não contém nenhuma regra de "como se agrupa por cidade" — ele itera sobre as dimensões.
  Acrescentar uma dimensão nova é acrescentar uma constante no enum.
- `VoteAdmission` (domínio) decide se um voto entra; o Flink apenas **guarda** o recibo
  anterior de cada eleitor no estado por chave. Trocar o mecanismo de estado não mexe na
  regra, e a regra é testável sem subir um cluster.

## Tópicos

| Tópico | Chave | Política |
|---|---|---|
| `votes.cast` | `voterId` | 6 partições |
| `votes.rejected` | `voterId` | duplicatas e votos inválidos, auditáveis |
| `votes.receipts` | hash do recibo | compactado |
| `results.by-candidate` / `-state` / `-city` / `-party` | valor da dimensão | compactado |

A chave de `votes.cast` **não é decorativa**: é o que coloca todos os votos de um eleitor na
mesma partição, condição para que o dedup por chave do Flink enxergue a duplicata. Trocá-la
quebra a regra "um voto por eleitor" sem quebrar nenhum teste unitário.

Os tópicos de resultado são compactados e keyed pela dimensão: o Kafka mantém a última
contagem de cada chave para sempre, então um consumidor que leia do início reconstrói o
placar completo.

## Teste de carga

```bash
make bench                                              # 10.000 votos em 30s, 10% duplicatas
make bench BENCH="-Dvotes=50000 -Dramp=60 -DduplicateRate=0.05"
# relatório em voting-benchmark/target/gatling/*/index.html
```

Uma rodada de referência nesta máquina (Colima, 4 vCPU): 10.000 requisições, **0 falhas**,
p95 **49 ms**, 322 req/s — e a apuração fechou em 8.986, exatamente o número de eleitores
distintos, com 1.014 duplicatas em `votes.rejected`.

### A base fixa

`voting-benchmark/src/main/resources/dataset/` é versionada, e não sorteada a cada execução:
duas rodadas só são comparáveis se disputarem a mesma eleição.

| Arquivo | Conteúdo |
|---|---|
| `partidos.csv` | os 30 partidos registrados no TSE, com número de legenda e sigla |
| `candidatos.csv` | 12 candidatos, um por partido, com nomes gerados pelo DataFaker (pt-BR) |
| `municipios.csv` | 87 municípios cobrindo os 27 estados, com peso de sorteio |

O `candidateId` é o **número da legenda** (13, 22, 45…), como na urna: o eleitor digita o
número do partido do candidato à presidência. Isso deixa as chaves de
`results.by-candidate` legíveis sem consultar outra tabela, e o `partyId` é a sigla — então
`results.by-party` sai como `PT`, `PL`, `NOVO`.

`make bench-data` regenera `candidatos.csv` a partir de `partidos.csv`. A semente é fixa
(`SEED = 2026`), então regerar produz o mesmo arquivo: se a lista de partidos mudar, dá para
ver no diff quem entrou e quem saiu, em vez de um arquivo inteiro embaralhado.

### O que é determinístico, e o que não é

O `VoteFeeder` é uma sequência determinística: para a mesma semente, a n-ésima cédula é
sempre a mesma — mesmo candidato, mesmo município, mesma posição das duplicatas. Qual usuário
virtual pega qual cédula varia com o escalonamento das threads, mas nenhum agregado depende
disso: os totais por candidato, estado, cidade e partido são idênticos entre execuções.

Duas escolhas deliberadas na distribuição:

- **A intenção de voto é desigual** (28%, 24%, 12%…). Um empate de doze vias não produziria
  chaves quentes, que é justamente o que estressa o particionamento da apuração.
- **Os municípios são sorteados por peso populacional.** São Paulo precisa receber mais votos
  que Rorainópolis, senão `results.by-state` fica uniforme e irreal.

O que **muda** a cada rodada é só o namespace dos eleitores: o `voterId` carrega um `runId`
com timestamp. Sem isso, a segunda execução contra o mesmo cluster teria todos os votos
recusados — o dedup do Flink lembra dos eleitores da rodada anterior. Fixe com `-DrunId=...`
apenas quando quiser exatamente esse cenário.

## Merkle Tree e prova de inclusão

O serviço `voting-merkle` (Go) sela cada janela de tempo numa árvore de Merkle **RFC 6962** —
a mesma do Certificate Transparency — encadeada com a janela anterior. Com o recibo em mãos,
o eleitor obtém uma prova de que seu voto entrou na apuração, e pode conferi-la **sem
confiar no servidor**.

```bash
curl localhost:8083/proof/$RECIBO   # prova de inclusão + raiz da janela
make roots                          # a cadeia de raízes já seladas
```

### Por que ele não lê `votes.receipts`

Esta é a decisão central do desenho. `votes.receipts` recebe um registro por voto que **chega
na API**, inclusive os que o Flink recusa depois. Uma árvore construída sobre aquele tópico
daria prova de inclusão para votos que nunca foram contados — o oposto exato da garantia que
ela existe para dar.

A árvore come do fluxo **pós-dedup**: o Flink publica em `votes.accepted` apenas os votos
admitidos. Verificado na prática: um voto duplicado aparece em `votes.receipts`, é recusado
como `DUPLICATE_VOTE`, e o serviço responde `404 RECIBO_NAO_SELADO` para o recibo dele.

`votes.accepted` também **não carrega o candidato**, pelo mesmo motivo que `votes.receipts`
não carrega: é a base de uma consulta pública, e viraria um mapa de quem votou em quem.

### Quem fecha a janela

O Flink, e não o serviço Go. Ele já tem marca d'água por horário do voto e exactly-once —
sabe dizer "a janela [T, T+n) fechou, não chega mais nada". Publica isso em `votes.windows`
como `{windowId, count}`, e o serviço Go sela quando junta exatamente `count` folhas.

Selar por **completude**, e não por timeout, é o que evita que um consumidor lento produza uma
raiz divergente da apuração. E se a contagem não fecha, existe uma lacuna — que fica visível,
em vez de virar uma árvore silenciosamente incompleta.

`votes.windows` tem **uma partição só**, de propósito: a ordem dos marcadores é o que define a
sequência da cadeia de raízes.

### O que torna a raiz reproduzível

Dentro de uma janela, as folhas são ordenadas pelo recibo antes de virar árvore. A raiz passa
a ser função do *conjunto*, e não da ordem de chegada — que varia a cada execução, já que as
folhas vêm de 6 partições em paralelo. Sem essa ordenação, nenhum auditor conseguiria
recalcular a raiz.

Cada checkpoint inclui o hash do anterior:

```
checkpoint_N = SHA-256(0x02 || checkpoint_{N-1} || raiz_N || windowId || tamanho)
```

Reescrever uma janela antiga muda todos os elos seguintes: a última raiz publicada compromete
a história inteira.

### Como o eleitor confere

Com recibo, prova e raiz, a conta é a da RFC 6962 e cabe em vinte linhas em qualquer
linguagem:

```
folha = SHA-256(0x00 || bytes(recibo))
nó    = SHA-256(0x01 || esquerda || direita)
```

Os prefixos `0x00`/`0x01` não são decoração: sem eles, uma folha pode ser forjada para se
passar por um nó interno (ataque de segunda pré-imagem). O `0x02` da cadeia existe pelo mesmo
motivo, para que um elo não colida com um nó de árvore.

Uma rodada real: 4 janelas seladas (12+7+7+7 folhas), prova conferida por um verificador
independente escrito em Python — folha forjada rejeitada, caminho adulterado rejeitado, cadeia
íntegra.

### Estrutura do módulo Go

O pedido era ser o mais agnóstico possível, então o núcleo não sabe o que é um voto:

| Pacote | Sabe sobre |
|---|---|
| `pkg/merkle` | árvores e provas. **Zero dependências**, zero vocabulário de eleição. |
| `pkg/checkpoint` | lotes de folhas opacas, selados e encadeados. Depende só de `pkg/merkle`. |
| `internal/voting` | os eventos da votação e como viram folhas. |
| `internal/kafkaio` | o único pacote que sabe que o transporte é Kafka. |
| `internal/api` | HTTP. |

### Desempenho

```bash
cd voting-merkle && go test ./pkg/... -run '^$' -bench . -benchtime 200x
```

Os benchmarks cobrem os dois caminhos que importam: selar uma janela (`New`, `Seal`) e servir
uma prova (`Prove`, `Lookup`), em lotes de 1 mil a 100 mil folhas.

Medido com profadvisor, em capturas adjacentes (as duas medições em sequência, sob a mesma
carga de máquina — capturas separadas no tempo variaram até 33% em código idêntico), num lote
de 100 mil folhas:

| | antes | depois | alocações |
|---|---|---|---|
| `Prove` | 14,86 ms | **226 ns** | 99.988 → 1 |
| `New` | 24,99 ms | **20,58 ms** | 200.001 → 42 |

A `Tree` guardava apenas as folhas e a raiz, então **cada prova reconstruía os nós internos do
zero** — O(n) hashes para responder a um único eleitor. Materializando os níveis na construção,
a prova virou um passeio de O(log n) leituras, sem hash nenhum. O custo é dobrar a memória da
árvore (2n hashes em vez de n): 6 MB para 100 mil folhas.

O ganho em `New` vem de reaproveitar o hasher e alocar um buffer por nível, em vez de um digest
por nó.

### Limitação conhecida: sem persistência

A cadeia vive **em memória**. Um restart relê os dois tópicos desde o início e reconstrói tudo
— por isso o consumidor usa um grupo efêmero por processo, em vez de retomar de um offset
salvo, que daria uma árvore com buracos.

Funciona porque `merkle.roots` é compactado e guarda a cadeia publicada, mas não escala para
uma eleição real: o tempo de partida cresce com o número de votos. Persistir folhas e
checkpoints é o próximo passo natural.

## Garantias

- **Um voto por eleitor** — estado por `voterId` no Flink. O segundo voto vai para
  `votes.rejected` com o motivo e o recibo do voto que de fato vale.
- **Nada é descartado em silêncio** — duplicatas, votos de outra eleição e eventos que violam
  as invariantes viram rejeições auditáveis. Só JSON ilegível é descartado (com log e o
  contador `corruptRecords`), para que uma mensagem corrompida não vire um loop de restart.
- **Exactly-once** — checkpointing a cada 10s e sinks transacionais. As contagens são
  acumulativas: com `at-least-once`, um restart reprocessaria votos já contados e o placar
  inflaria. Custa latência — os resultados aparecem ao fim de cada checkpoint. Desligue com
  `--exactly.once false` se quiser latência menor em desenvolvimento.
- **O voto só é confirmado depois do ack do Kafka** — a publicação na ingestão é síncrona,
  com `acks=all` e idempotência. Um `503` significa "o voto está bom, o sistema é que não
  conseguiu registrá-lo"; reenviar é seguro, porque a duplicata seria filtrada na apuração.
- **Prova de inclusão auditável** — cada janela é selada numa árvore RFC 6962 com raiz
  encadeada à anterior. O eleitor confere seu recibo sem confiar no servidor.
- **O prazo é do servidor** — `castAt` é carimbado na chegada e não existe no contrato HTTP.
  A autoridade sobre o encerramento é o Flink, não a borda.

### Verificado na prática

| Garantia | Evidência |
|---|---|
| Um voto por eleitor | 11.001 requisições, 10.000 eleitores → apuração fechou em 8.986 (os distintos), 1.014 recusas |
| Exactly-once | `restart taskmanager` durante a carga: contagens não inflaram, estado do dedup preservado |
| Prova de inclusão | verificador independente em Python: prova válida aceita, folha forjada e caminho adulterado rejeitados |
| Duplicata sem prova | recibo do voto duplicado existe em `votes.receipts`, é recusado, e recebe `404` do serviço de prova |
| Encerramento | voto injetado direto no Kafka com `castAt` após o prazo → `ELECTION_CLOSED` |
| Janela sem tráfego | voto solitário selado em ~45s pelos heartbeats (antes: `404` indefinidamente) |

## Nota de privacidade sobre o recibo

O recibo é `SHA-256(electionId | voterId | candidateId | castAt | pepper)`, com o `pepper`
sendo um segredo do servidor.

**O `pepper` é o que impede a quebra do sigilo.** Sem ele, o espaço de candidatos é pequeno o
bastante para que qualquer pessoa que conheça o `voterId` recalcule o hash para cada candidato
e descubra o voto. Em produção o `pepper` vem do ambiente (`VOTING_RECEIPT_PEPPER`) e nunca do
repositório — o valor no `docker-compose.yml` serve apenas para desenvolvimento.

Mesmo com o `pepper`, o desenho tem um limite conhecido: quem obtiver o segredo do servidor
consegue reconstruir o voto de qualquer eleitor. A próxima fase deve migrar para um
*commitment* com nonce aleatório por voto, guardado apenas com o eleitor. Por isso
`votes.receipts` deliberadamente **não** carrega o candidato — um tópico que ligasse recibo a
candidato seria um mapa do voto de cada eleitor.

## Próximas fases

1. **Persistência da cadeia Merkle.** Ver a limitação conhecida acima: hoje a árvore vive em
   memória e um restart relê tudo.
2. **Frontend de confirmação.** O backend está pronto: `GET /proof/{recibo}` devolve prova e
   raiz. Falta a tela onde o eleitor cola o hash — e, idealmente, que a verificação rode no
   navegador dele, não no servidor.
3. **Prova de consistência entre raízes** (`GET /consistency?from=N&to=M`), provando que uma
   árvore é extensão da outra e que nada foi reescrito no meio.
4. **Agregação por janela antes de publicar.** Hoje cada voto emite uma atualização de
   contagem — volumoso demais para uma eleição real. A janela da Merkle Tree já existe e
   resolve.
5. **Tolerância de atraso no encerramento.** Hoje o prazo é estrito: vale o `castAt`
   carimbado, sem folga. Um voto legítimo com latência alta na fronteira é recusado. A folga
   seria um campo em `ElectionSchedule`, e precisa ser menor que o intervalo até a publicação
   da raiz final.

## Notas de implementação

- Os contratos são `record`s, que não satisfazem o contrato de POJO do Flink. Em vez de cair
  no Kryo (que não instancia records), o job usa `JsonTypeInfo`/`JsonTypeSerializer`: o mesmo
  JSON do tópico também entre operadores e no estado. Um só formato para depurar, e estado que
  sobrevive à adição de campos. Se o volume tornar isso caro, o caminho é um formato binário
  nos contratos — não um remendo no serializador.
- `transaction.timeout.ms` dos sinks (900000) precisa ser ≤ `transaction.max.timeout.ms` do
  broker, senão o produtor transacional é recusado e o job não sobe. Os dois estão fixados no
  `docker-compose.yml`.
- A imagem do Flink é customizada (`infra/flink/Dockerfile`) apenas para que
  `/flink-checkpoints` pertença ao usuário `flink` — sem isso o volume nomeado nasce como root
  e o JobManager falha ao criar o checkpoint.

## Encerramento da votação

```bash
VOTING_OPENS_AT=2026-10-04T11:00:00Z VOTING_CLOSES_AT=2026-10-04T20:00:00Z make up
make submit JOB_ARGS="--bootstrap.servers kafka:9092 \
  --election.opens.at 2026-10-04T11:00:00Z --election.closes.at 2026-10-04T20:00:00Z"
```

Sem as duas variáveis, a eleição não tem prazo — conveniente em desenvolvimento, e um erro de
operação em produção.

### O horário do voto é do servidor

`castAt` **não existe no contrato HTTP**. O servidor carimba o momento da chegada e ignora
qualquer campo que o cliente mande. Enviar `"castAt":"2026-10-04T12:00:00Z"` num POST depois do
prazo continua devolvendo `403`.

Não há como saber o instante do clique sem confiar no relógio do dispositivo, e confiar nele
tornaria o encerramento contornável por antedatação. O preço é que o carimbo inclui a latência
de rede — diferença sub-segundo, relevante apenas na fronteira do prazo.

### Duas camadas, uma autoridade

A API recusa com `403 VOTACAO_FECHADA` por cortesia, para o eleitor não receber um comprovante
que a apuração vai descartar. **A autoridade é o Flink**, pelo mesmo motivo que já é para a
unicidade: é o ponto único que vê todos os votos com um relógio só.

Verificado injetando um voto direto no Kafka, driblando a API por completo, com `castAt` cinco
segundos após o prazo — o Flink o recusou como `ELECTION_CLOSED` em `votes.rejected`.

`ELECTION_NOT_OPEN` é um motivo separado de `ELECTION_CLOSED`: as duas situações pedem
investigações diferentes — uma sugere relógio adiantado, a outra é o caso normal de quem votou
tarde demais.

## O travamento da marca d'água, e por que heartbeats

Este foi o problema mais sutil do sistema, e vale registrar por inteiro.

A marca d'água do Flink é derivada **do dado**: `forBoundedOutOfOrderness(5s)` emite
`maior_castAt_visto - 5s`. Sem evento novo, não há novo máximo, e ela congela. A janela só
dispara quando a marca d'água passa o fim dela — que nunca chega.

Consequência medida antes da correção: **um voto solitário nunca era selado**. Noventa
segundos, seis verificações, `404` em todas. O eleitor jamais receberia prova de inclusão. E
isso acontecia justamente no momento mais crítico — a cauda de uma eleição, logo antes do
encerramento.

`withIdleness(30s)` **não resolve**, ao contrário do que o nome sugere: ele marca uma
*partição* como ociosa para que ela não segure a marca d'água combinada das outras. Quando
todas estão ociosas, a marca d'água simplesmente para.

### A correção óbvia destruiria a auditoria

O reflexo é usar `ProcessingTimeoutTrigger`, que dispara a janela por tempo de processamento.
Funciona — e quebra a Merkle Tree. O conteúdo de cada janela passaria a depender do relógio de
parede durante o processamento: dois reprocessamentos do mesmo log, em máquinas de velocidades
diferentes, cortariam as janelas em pontos diferentes e produziriam **raízes diferentes**. Uma
raiz que não é reproduzível não prova nada.

### A correção: o tempo vira dado

`votes.control` não traz votos, traz o tempo:

- **Heartbeat** a cada poucos segundos, com o horário do servidor. Publicado pela API de
  ingestão, porque é lá que mora o mesmo relógio que carimba os votos.
- **Sentinela de encerramento**, uma vez, quando o prazo passa. Empurra a marca d'água além da
  última janela e faz a raiz final ser publicada.

Os dois entram no fluxo unidos aos votos, com marca d'água única, e são descartados logo depois
do filtro — cumpriram seu papel só por terem existido no fluxo com um horário. Como tudo
permanece em tempo de evento, o replay reproduz as mesmas janelas e as mesmas raízes.

O intervalo do heartbeat precisa ser confortavelmente menor que a janela da Merkle Tree: é ele
que faz uma janela sem votos fechar, e uma janela que não fecha é um grupo de eleitores sem
prova de inclusão.

Depois da correção, o mesmo voto solitário sela em ~45s (janela de 15s + marca d'água de 5s +
heartbeat + checkpoint) e a prova responde `200`.
