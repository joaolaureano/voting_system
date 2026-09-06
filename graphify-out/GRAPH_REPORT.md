# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 99 files · ~27,446 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 709 nodes · 1913 edges · 24 communities (15 shown, 9 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 138 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `7f1335c5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- VoteAggregationJob.java
- JobConfig
- DatasetTest.java
- com.fasterxml.jackson.databind.ObjectMapper
- org.junit.jupiter.api.Test
- ControlEvent
- testing.T
- ApiExceptionHandler.java
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
- ElectionSchedule
- RejectionReason
- VoteFeeder

## God Nodes (most connected - your core abstractions)
1. `ElectionSchedule` - 47 edges
2. `Vote` - 46 edges
3. `VoteCastEvent` - 34 edges
4. `VoteReceipt` - 32 edges
5. `JobConfig` - 28 edges
6. `JsonTypeInfo` - 25 edges
7. `ElectionId` - 24 edges
8. `CastVoteUseCase` - 20 edges
9. `Log` - 20 edges
10. `NewLog()` - 20 edges

## Surprising Connections (you probably didn't know these)
- `KafkaReceiptPublisher` --implements--> `ReceiptPublisher`  [EXTRACTED]
  voting-ingest-api/src/main/java/com/voting/ingest/kafka/KafkaReceiptPublisher.java → voting-application/src/main/java/com/voting/application/port/ReceiptPublisher.java
- `KafkaVoteEventPublisher` --implements--> `VoteEventPublisher`  [EXTRACTED]
  voting-ingest-api/src/main/java/com/voting/ingest/kafka/KafkaVoteEventPublisher.java → voting-application/src/main/java/com/voting/application/port/VoteEventPublisher.java
- `CastVoteResult` --references--> `Vote`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/model/Vote.java
- `CastVoteResult` --references--> `VoteReceipt`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/receipt/VoteReceipt.java
- `VoteController` --references--> `CastVoteUseCase`  [EXTRACTED]
  voting-ingest-api/src/main/java/com/voting/ingest/web/VoteController.java → voting-application/src/main/java/com/voting/application/usecase/CastVoteUseCase.java

## Import Cycles
- None detected.

## Communities (24 total, 9 thin omitted)

### Community 1 - "VoteAggregationJob.java"
Cohesion: 0.06
Nodes (35): org.apache.flink.api.common.ExecutionConfig, org.apache.flink.api.common.state.ValueState, org.apache.flink.api.java.functions.KeySelector, org.apache.flink.configuration.Configuration, org.apache.flink.streaming.api.functions.KeyedProcessFunction, org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction, org.apache.flink.streaming.api.windowing.windows.TimeWindow, org.apache.flink.util.OutputTag (+27 more)

### Community 2 - "JobConfig"
Cohesion: 0.08
Nodes (13): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.connector.kafka.source.KafkaSource, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, Override (+5 more)

### Community 3 - "DatasetTest.java"
Cohesion: 0.15
Nodes (6): Candidato, CandidatosGenerator, Dataset, Municipio, Partido, DatasetTest

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.07
Nodes (25): ClassLoader, com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot (+17 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.06
Nodes (16): org.junit.jupiter.api.Test, VoteControllerTest.PortasEmMemoria, CastVoteCommand, CastVoteUseCaseTest, VoteEventMapperTest, Accepted, AdmissionDecision, Rejected (+8 more)

### Community 6 - "ControlEvent"
Cohesion: 0.11
Nodes (13): org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.kafka.core.KafkaTemplate, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, ControlEvent, ReceiptEvent, VotingProperties, ControlEventPublisher (+5 more)

### Community 7 - "testing.T"
Cohesion: 0.06
Nodes (72): pending, net/http/httptest.ResponseRecorder, sync.RWMutex, testing.B, testing.T, NewServer(), cadeiaComJanelas(), contains() (+64 more)

### Community 8 - "ApiExceptionHandler.java"
Cohesion: 0.12
Nodes (14): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException, CastVoteResult (+6 more)

### Community 9 - "Server"
Cohesion: 0.15
Nodes (14): errorResponse, ProofResponse, RootResponse, Server, net/http.Handler, net/http.Request, net/http.ResponseWriter, time.Time (+6 more)

### Community 10 - "Consumer"
Cohesion: 0.16
Nodes (12): context.Context, log/slog.Logger, time.Duration, kafka.Reader, kafka.Writer, Config, Consumer, Publisher (+4 more)

### Community 11 - "Sistema de Votação em Tempo Real — Kafka + Flink"
Cohesion: 0.07
Nodes (29): A base fixa, A correção: o tempo vira dado, A correção óbvia destruiria a auditoria, Arquitetura, Como medir sem se enganar, Como o eleitor confere, Como rodar, Desempenho (+21 more)

### Community 12 - "IngestApiApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling, IngestApiApplication

### Community 22 - "ElectionSchedule"
Cohesion: 0.06
Nodes (38): FunctionalInterface, java.util.regex.Pattern, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration (+30 more)

### Community 32 - "RejectionReason"
Cohesion: 0.29
Nodes (6): RejectionReason, DUPLICATE_VOTE, ELECTION_CLOSED, ELECTION_NOT_OPEN, INVALID_VOTE, WRONG_ELECTION

### Community 34 - "VoteFeeder"
Cohesion: 0.14
Nodes (8): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Override, VoteFeeder, VoteFeederTest, Override, VoteSimulation

## Knowledge Gaps
- **40 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+35 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ElectionSchedule` connect `ElectionSchedule` to `VoteAggregationJob.java`, `JobConfig`, `org.junit.jupiter.api.Test`, `ControlEvent`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Why does `Vote` connect `ElectionSchedule` to `VoteAggregationJob.java`, `JobConfig`, `org.junit.jupiter.api.Test`, `ControlEvent`, `ApiExceptionHandler.java`?**
  _High betweenness centrality (0.052) - this node is a cross-community bridge._
- **Why does `VoteCastEvent` connect `VoteAggregationJob.java` to `JobConfig`, `ElectionSchedule`?**
  _High betweenness centrality (0.036) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `ElectionSchedule` (e.g. with `.oUltimoMilissegundoAntesDoPrazoAindaVale()` and `.recusaVotoAntesDaAbertura()`) actually correct?**
  _`ElectionSchedule` has 4 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _40 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `VoteAggregationJob.java` be split into smaller, more focused modules?**
  _Cohesion score 0.05555555555555555 - nodes in this community are weakly interconnected._
- **Should `JobConfig` be split into smaller, more focused modules?**
  _Cohesion score 0.08413461538461539 - nodes in this community are weakly interconnected._