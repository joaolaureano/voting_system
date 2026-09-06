# Sistema de Votação em Tempo Real — Kafka + Flink

Apuração de votos em streaming: os votos entram por uma API REST, vão para o Kafka e um job
Flink garante **um voto por eleitor** e mantém a contagem corrente por **candidato, estado,
cidade e partido**.

```
POST /api/v1/votes ──► ingest-api ──► [votes.cast] ──► Flink ──┬─► [results.by-candidate]
                            │                            │     ├─► [results.by-state]
                            │                          dedup   ├─► [results.by-city]
                            └──► [votes.receipts]         │     └─► [results.by-party]
                                                          └─────► [votes.rejected]
```

## Como rodar

Requisitos: Docker e um JDK 17+ (`JAVA_HOME`; o `Makefile` usa `/opt/homebrew/opt/openjdk@21`
por padrão — sobrescreva com `make JAVA_HOME=... `).

```bash
make test        # 58 testes: domínio, contratos, caso de uso, pipeline Flink, API e base do benchmark
make up          # Kafka (KRaft), Flink, API de ingestão e kafka-ui
make submit      # submete o job de apuração
make bench       # teste de carga com Gatling (10.000 votos, 10% duplicatas)
make results     # placar corrente por candidato e por estado
make rejected    # votos recusados
make down        # derruba tudo e apaga os dados
```

| Serviço | Endereço |
|---|---|
| API de ingestão | http://localhost:8081 |
| Interface do Flink | http://localhost:8082 |
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

1. **Merkle Tree por janela de tempo.** O recibo determinístico já é a folha da árvore, e as
   marcas d'água por horário do voto já estão no job — falta a janela e a publicação da raiz.
2. **Frontend de confirmação.** O eleitor consulta seu hash e vê se o voto entrou. O gancho é
   `votes.receipts`, compactado e keyed pelo hash; falta um serviço de leitura sobre ele.
3. **Agregação por janela antes de publicar.** Hoje cada voto emite uma atualização de
   contagem — volumoso demais para uma eleição real. A mesma janela da Merkle Tree resolve.

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
