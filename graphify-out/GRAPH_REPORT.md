# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 112 files · ~37,497 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 833 nodes · 2244 edges · 32 communities (23 shown, 9 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 153 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ecaa6df7`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- org.junit.jupiter.api.Test
- VoteAggregationJob.java
- JobConfig
- VoteFeeder
- com.fasterxml.jackson.databind.ObjectMapper
- .execute
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
- VoteReceipt
- VoteControllerClosedElectionTest.java
- VoteAdmissionTest.java
- Log
- ElectionId
- org.springframework.context.annotation.Configuration
- Log
- RejectionReason

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
10. `NewLog()` - 23 edges

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

## Communities (32 total, 9 thin omitted)

### Community 0 - "org.junit.jupiter.api.Test"
Cohesion: 0.06
Nodes (16): org.junit.jupiter.api.Test, VoteControllerTest.PortasEmMemoria, Accepted, AdmissionDecision, Rejected, VoteAdmission, ElectionClosedException, ElectionSchedule (+8 more)

### Community 1 - "VoteAggregationJob.java"
Cohesion: 0.05
Nodes (38): Deprecated, org.apache.flink.api.common.ExecutionConfig, org.apache.flink.api.common.state.ValueState, org.apache.flink.api.java.functions.KeySelector, org.apache.flink.configuration.Configuration, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, org.apache.flink.streaming.api.functions.KeyedProcessFunction, org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction (+30 more)

### Community 2 - "JobConfig"
Cohesion: 0.10
Nodes (11): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.connector.kafka.source.KafkaSource, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator, Override, JobConfig (+3 more)

### Community 3 - "VoteFeeder"
Cohesion: 0.07
Nodes (14): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Candidato, CandidatosGenerator, Dataset, Municipio, Partido (+6 more)

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.07
Nodes (25): ClassLoader, com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot (+17 more)

### Community 5 - ".execute"
Cohesion: 0.14
Nodes (10): org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, CastVoteCommand, CastVoteResult, CastVoteUseCase, CastVoteUseCaseTest, CastVoteRequest (+2 more)

### Community 6 - "VotingProperties"
Cohesion: 0.10
Nodes (14): com.fasterxml.jackson.databind.JsonNode, org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.kafka.core.KafkaTemplate, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, VotingProperties, ControlEventPublisher, KafkaEventPublisher (+6 more)

### Community 7 - "testing.T"
Cohesion: 0.08
Nodes (71): net/http/httptest.ResponseRecorder, testing.T, NewServer(), cadeiaComJanelas(), contains(), get(), recibo(), TestAProvaDaApiVerificaContraARaiz() (+63 more)

### Community 8 - "DomainException"
Cohesion: 0.14
Nodes (10): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException, DomainException, InvalidElectionScheduleException, InvalidTallyException, InvalidWindowException (+2 more)

### Community 9 - "Server"
Cohesion: 0.16
Nodes (14): errorResponse, ProofResponse, RootResponse, Server, net/http.Handler, net/http.Request, net/http.ResponseWriter, time.Time (+6 more)

### Community 10 - "Consumer"
Cohesion: 0.15
Nodes (12): context.Context, log/slog.Logger, time.Duration, kafka.Reader, kafka.Writer, Config, Consumer, Publisher (+4 more)

### Community 11 - "Sistema de Votação em Tempo Real — Kafka + Flink"
Cohesion: 0.06
Nodes (33): A base fixa, A correção: o tempo vira dado, A correção óbvia destruiria a auditoria, A partida é uma auditoria do próprio disco, Arquitetura, Como medir sem se enganar, Como o eleitor confere, Como rodar (+25 more)

### Community 12 - "IngestApiApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling, IngestApiApplication

### Community 22 - "Vote"
Cohesion: 0.14
Nodes (10): CandidateId, Override, Override, PartyId, Region, Vote, Override, VoterId (+2 more)

### Community 23 - "ReceiptPublisher"
Cohesion: 0.13
Nodes (9): FunctionalInterface, org.springframework.context.annotation.Bean, ReceiptPublisher, VoteEventPublisher, ReceiptPolicy, VotingConfiguration, EventPublicationException, VotacaoEncerrada (+1 more)

### Community 24 - "VoteReceipt"
Cohesion: 0.16
Nodes (5): java.util.regex.Pattern, ReceiptEvent, VoteEventMapperTest, Override, VoteReceipt

### Community 25 - "VoteControllerClosedElectionTest.java"
Cohesion: 0.20
Nodes (8): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.test.web.servlet.MockMvc, VoteControllerClosedElectionTest.VotacaoEncerrada, Override, Sha256ReceiptPolicy, VoteControllerClosedElectionTest

### Community 26 - "VoteAdmissionTest.java"
Cohesion: 0.27
Nodes (4): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, Votes, VoteTallyTest

### Community 27 - "Log"
Cohesion: 0.06
Nodes (48): Kind, pending, Sealed, Store, sync.RWMutex, testing.B, Journal, abrirCadeia() (+40 more)

### Community 28 - "ElectionId"
Cohesion: 0.28
Nodes (3): WrongElectionException, ElectionId, Override

### Community 29 - "org.springframework.context.annotation.Configuration"
Cohesion: 0.43
Nodes (5): org.springframework.context.annotation.Configuration, org.springframework.web.servlet.config.annotation.PathMatchConfigurer, org.springframework.web.servlet.config.annotation.WebMvcConfigurer, ApiVersionConfiguration, Override

### Community 30 - "Log"
Cohesion: 0.20
Nodes (5): bufio.Writer, os.File, Log, Open(), TestCabecalhoEstranhoNaoEAbertoComoLog()

### Community 32 - "RejectionReason"
Cohesion: 0.29
Nodes (6): RejectionReason, DUPLICATE_VOTE, ELECTION_CLOSED, ELECTION_NOT_OPEN, INVALID_VOTE, WRONG_ELECTION

## Knowledge Gaps
- **43 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+38 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ElectionSchedule` connect `org.junit.jupiter.api.Test` to `VoteAggregationJob.java`, `JobConfig`, `.execute`, `VotingProperties`, `Vote`, `ReceiptPublisher`, `VoteReceipt`, `VoteControllerClosedElectionTest.java`, `VoteAdmissionTest.java`, `ElectionId`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `Vote` connect `Vote` to `org.junit.jupiter.api.Test`, `VoteAggregationJob.java`, `JobConfig`, `.execute`, `VotingProperties`, `ReceiptPublisher`, `VoteReceipt`, `VoteControllerClosedElectionTest.java`, `VoteAdmissionTest.java`, `ElectionId`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **Why does `ElectionId` connect `ElectionId` to `org.junit.jupiter.api.Test`, `VoteAggregationJob.java`, `JobConfig`, `.execute`, `VotingProperties`, `Vote`, `ReceiptPublisher`, `VoteControllerClosedElectionTest.java`, `VoteAdmissionTest.java`?**
  _High betweenness centrality (0.027) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `ElectionSchedule` (e.g. with `.oUltimoMilissegundoAntesDoPrazoAindaVale()` and `.recusaVotoAntesDaAbertura()`) actually correct?**
  _`ElectionSchedule` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _43 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `org.junit.jupiter.api.Test` be split into smaller, more focused modules?**
  _Cohesion score 0.06455696202531645 - nodes in this community are weakly interconnected._
- **Should `VoteAggregationJob.java` be split into smaller, more focused modules?**
  _Cohesion score 0.053194333066025126 - nodes in this community are weakly interconnected._