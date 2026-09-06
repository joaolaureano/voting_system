# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 120 files · ~43,761 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 910 nodes · 2366 edges · 49 communities (34 shown, 15 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 154 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `d56074cd`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ElectionSchedule
- VoteAggregationJob.java
- JobConfig
- VoteFeeder
- com.fasterxml.jackson.databind.ObjectMapper
- app.js
- VotingProperties
- testing.T
- DomainException
- Server
- Consumer
- Sistema de Votação em Tempo Real — Kafka + Flink
- IngestApiApplication
- Identifiers
- com.voting:voting-system
- voting-application
- voting-benchmark
- voting-contracts
- voting-domain
- voting-ingest-api
- voting-streaming
- github.com/joaolaureano/voting_system/voting-merkle
- Vote
- ReceiptPublisher
- VoteCastEvent
- .execute
- VoteAdmissionTest.java
- Journal
- org.junit.jupiter.api.Test
- org.springframework.context.annotation.Configuration
- Log
- TallyDimension
- RejectionReason
- Log
- .voto
- JsonTypeInfo
- journal_test.go
- check.mjs
- Leaf
- package.json
- voting-web
- Proof
- .toDomain
- vetores/main.go
- .containing
- VoteAggregationPipelineTest.java
- WindowMarkerFunction
- VoteTally
- .toReceiptEvent

## God Nodes (most connected - your core abstractions)
1. `ElectionSchedule` - 50 edges
2. `Vote` - 46 edges
3. `VoteCastEvent` - 34 edges
4. `VoteReceipt` - 32 edges
5. `ElectionId` - 31 edges
6. `Log` - 31 edges
7. `JobConfig` - 28 edges
8. `JsonTypeInfo` - 25 edges
9. `Checkpoint` - 24 edges
10. `NewLog()` - 24 edges

## Surprising Connections (you probably didn't know these)
- `KafkaReceiptPublisher` --implements--> `ReceiptPublisher`  [EXTRACTED]
  voting-ingest-api/src/main/java/com/voting/ingest/kafka/KafkaReceiptPublisher.java → voting-application/src/main/java/com/voting/application/port/ReceiptPublisher.java
- `KafkaVoteEventPublisher` --implements--> `VoteEventPublisher`  [EXTRACTED]
  voting-ingest-api/src/main/java/com/voting/ingest/kafka/KafkaVoteEventPublisher.java → voting-application/src/main/java/com/voting/application/port/VoteEventPublisher.java
- `CastVoteResult` --references--> `Vote`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/model/Vote.java
- `CastVoteResult` --references--> `VoteReceipt`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/receipt/VoteReceipt.java
- `CastVoteUseCase` --references--> `ElectionSchedule`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteUseCase.java → voting-domain/src/main/java/com/voting/domain/election/ElectionSchedule.java

## Import Cycles
- None detected.

## Communities (49 total, 15 thin omitted)

### Community 0 - "ElectionSchedule"
Cohesion: 0.16
Nodes (10): VoteEventMapperTest, Accepted, AdmissionDecision, Rejected, VoteAdmission, ElectionSchedule, Override, VoteReceipt (+2 more)

### Community 1 - "VoteAggregationJob.java"
Cohesion: 0.18
Nodes (13): org.apache.flink.api.common.ExecutionConfig, org.apache.flink.api.common.state.ValueState, org.apache.flink.configuration.Configuration, org.apache.flink.streaming.api.functions.KeyedProcessFunction, org.apache.flink.util.OutputTag, org.slf4j.Logger, AcceptedVoteEvent, RejectedVoteEvent (+5 more)

### Community 2 - "JobConfig"
Cohesion: 0.11
Nodes (11): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.connector.kafka.source.KafkaSource, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, Override (+3 more)

### Community 3 - "VoteFeeder"
Cohesion: 0.07
Nodes (14): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Candidato, CandidatosGenerator, Dataset, Municipio, Partido (+6 more)

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.07
Nodes (25): ClassLoader, com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot (+17 more)

### Community 5 - "app.js"
Cohesion: 0.11
Nodes (35): RFC-6962, aviso(), botaoCadeia, botaoConferir, campoRecibo, cartaoAceito(), cartaoCadeia(), cartaoErro() (+27 more)

### Community 6 - "VotingProperties"
Cohesion: 0.11
Nodes (14): com.fasterxml.jackson.databind.JsonNode, org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.kafka.core.KafkaTemplate, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, VotingProperties, ControlEventPublisher, KafkaEventPublisher (+6 more)

### Community 7 - "testing.T"
Cohesion: 0.08
Nodes (67): net/http/httptest.ResponseRecorder, testing.T, NewServer(), cadeiaComJanelas(), contains(), get(), recibo(), TestAProvaDaApiVerificaContraARaiz() (+59 more)

### Community 8 - "DomainException"
Cohesion: 0.10
Nodes (12): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException, DomainException, ElectionClosedException, InvalidElectionScheduleException, InvalidTallyException (+4 more)

### Community 9 - "Server"
Cohesion: 0.30
Nodes (8): errorResponse, RootResponse, Server, net/http.Handler, net/http.Request, net/http.ResponseWriter, toRootResponse(), writeJSON()

### Community 10 - "Consumer"
Cohesion: 0.16
Nodes (12): context.Context, log/slog.Logger, time.Duration, kafka.Reader, kafka.Writer, Config, Consumer, Publisher (+4 more)

### Community 11 - "Sistema de Votação em Tempo Real — Kafka + Flink"
Cohesion: 0.06
Nodes (34): A base fixa, A correção: o tempo vira dado, A correção óbvia destruiria a auditoria, A partida é uma auditoria do próprio disco, A tela do eleitor, Arquitetura, Como medir sem se enganar, Como o eleitor confere (+26 more)

### Community 12 - "IngestApiApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling, IngestApiApplication

### Community 22 - "Vote"
Cohesion: 0.13
Nodes (11): WrongElectionException, CandidateId, Override, ElectionId, Override, Override, PartyId, Region (+3 more)

### Community 23 - "ReceiptPublisher"
Cohesion: 0.10
Nodes (16): FunctionalInterface, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Import, org.springframework.test.web.servlet.MockMvc, VoteControllerClosedElectionTest.VotacaoEncerrada, ReceiptPublisher (+8 more)

### Community 24 - "VoteCastEvent"
Cohesion: 0.27
Nodes (3): VoteCastEvent, Context, Override

### Community 25 - ".execute"
Cohesion: 0.14
Nodes (10): org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, CastVoteCommand, CastVoteResult, CastVoteUseCase, CastVoteUseCaseTest, CastVoteRequest (+2 more)

### Community 26 - "VoteAdmissionTest.java"
Cohesion: 0.22
Nodes (5): java.util.regex.Pattern, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, Votes, VoteTallyTest

### Community 27 - "Journal"
Cohesion: 0.16
Nodes (10): Kind, Sealed, Journal, conferir(), Record, appendBytes(), clone(), decode() (+2 more)

### Community 28 - "org.junit.jupiter.api.Test"
Cohesion: 0.17
Nodes (4): org.junit.jupiter.api.Test, VoteControllerTest.PortasEmMemoria, ElectionScheduleTest, VoteControllerTest

### Community 29 - "org.springframework.context.annotation.Configuration"
Cohesion: 0.43
Nodes (5): org.springframework.context.annotation.Configuration, org.springframework.web.servlet.config.annotation.PathMatchConfigurer, org.springframework.web.servlet.config.annotation.WebMvcConfigurer, ApiVersionConfiguration, Override

### Community 30 - "Log"
Cohesion: 0.11
Nodes (19): bufio.Writer, os.File, testing.B, BenchmarkLookup(), BenchmarkSeal(), loteDe(), BenchmarkAppend(), BenchmarkRestore() (+11 more)

### Community 31 - "TallyDimension"
Cohesion: 0.16
Nodes (9): org.apache.flink.api.java.functions.KeySelector, keyOf(), TallyDimension, CANDIDATE, CITY, PARTY, STATE, DimensionKeySelector (+1 more)

### Community 32 - "RejectionReason"
Cohesion: 0.29
Nodes (6): RejectionReason, DUPLICATE_VOTE, ELECTION_CLOSED, ELECTION_NOT_OPEN, INVALID_VOTE, WRONG_ELECTION

### Community 33 - "Log"
Cohesion: 0.20
Nodes (10): pending, Store, sync.RWMutex, abrirCadeia(), chainHash(), GenesisHash(), Checkpoint, Log (+2 more)

### Community 35 - "JsonTypeInfo"
Cohesion: 0.20
Nodes (4): Deprecated, Override, SuppressWarnings, JsonTypeInfo

### Community 36 - "journal_test.go"
Cohesion: 0.32
Nodes (15): abrir(), be32(), cadeia(), crcDe(), folha(), putBE32(), recalcularCRCs(), selar() (+7 more)

### Community 37 - "check.mjs"
Cohesion: 0.13
Nodes (11): casos, erros, janela1, janela12, MIME, provas, RAIZ, roots (+3 more)

### Community 38 - "Leaf"
Cohesion: 0.24
Nodes (7): ProofResponse, time.Time, AcceptedVote, NewRootRecord(), Leaf, RootRecord, WindowMarker

### Community 39 - "package.json"
Cohesion: 0.22
Nodes (8): description, name, private, scripts, test, test:browser, type, version

### Community 40 - "voting-web"
Cohesion: 0.25
Nodes (7): Estrutura, No navegador de verdade, O que a tela diz, e o que ela se recusa a dizer, Rodar, Sem build, sem framework, sem CDN, Testes, voting-web

### Community 43 - "vetores/main.go"
Cohesion: 0.60
Nodes (4): janela, prova, saida, main()

### Community 46 - "WindowMarkerFunction"
Cohesion: 0.33
Nodes (6): org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction, org.apache.flink.streaming.api.windowing.windows.TimeWindow, WindowMarkerEvent, Context, Override, WindowMarkerFunction

### Community 47 - "VoteTally"
Cohesion: 0.31
Nodes (3): VoteTally, Context, Override

## Knowledge Gaps
- **76 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+71 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **15 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ElectionSchedule` connect `ElectionSchedule` to `VoteAggregationJob.java`, `JobConfig`, `.voto`, `VotingProperties`, `DomainException`, `VoteAggregationPipelineTest.java`, `Vote`, `ReceiptPublisher`, `.execute`, `VoteAdmissionTest.java`, `org.junit.jupiter.api.Test`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `Vote` connect `Vote` to `ElectionSchedule`, `VoteAggregationJob.java`, `.voto`, `VotingProperties`, `.toDomain`, `VoteAggregationPipelineTest.java`, `.toReceiptEvent`, `ReceiptPublisher`, `VoteCastEvent`, `.execute`, `VoteAdmissionTest.java`, `org.junit.jupiter.api.Test`, `TallyDimension`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Why does `ElectionId` connect `Vote` to `ElectionSchedule`, `VoteAggregationJob.java`, `.voto`, `JobConfig`, `VotingProperties`, `VoteAggregationPipelineTest.java`, `ReceiptPublisher`, `.execute`, `VoteAdmissionTest.java`, `org.junit.jupiter.api.Test`?**
  _High betweenness centrality (0.023) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `ElectionSchedule` (e.g. with `.oUltimoMilissegundoAntesDoPrazoAindaVale()` and `.recusaVotoAntesDaAbertura()`) actually correct?**
  _`ElectionSchedule` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _76 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `JobConfig` be split into smaller, more focused modules?**
  _Cohesion score 0.10897435897435898 - nodes in this community are weakly interconnected._
- **Should `VoteFeeder` be split into smaller, more focused modules?**
  _Cohesion score 0.07337526205450734 - nodes in this community are weakly interconnected._