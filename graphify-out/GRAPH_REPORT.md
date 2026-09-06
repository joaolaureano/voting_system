# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 97 files · ~26,084 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 699 nodes · 1895 edges · 33 communities (19 shown, 14 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 134 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `066e5ba8`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Vote
- JsonTypeInfo
- JobConfig
- VoteFeeder
- com.fasterxml.jackson.databind.ObjectMapper
- org.junit.jupiter.api.Test
- VotingProperties
- testing.T
- org.slf4j.Logger
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
- CastVoteUseCase
- VoteReceipt
- .voto
- VoteAggregationJob.java
- TallyUpdateEvent
- TallyDimension
- VoteCastEvent
- TimelineEvent
- .toDomain
- VoteControllerClosedElectionTest.java
- Sha256ReceiptPolicy

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
10. `CastVoteUseCaseTest` - 19 edges

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

## Communities (33 total, 14 thin omitted)

### Community 0 - "Vote"
Cohesion: 0.15
Nodes (11): CandidateId, Override, ElectionId, Override, Override, PartyId, Region, Vote (+3 more)

### Community 2 - "JobConfig"
Cohesion: 0.11
Nodes (9): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, Override, JobConfig, TypeInformation (+1 more)

### Community 3 - "VoteFeeder"
Cohesion: 0.07
Nodes (14): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Candidato, CandidatosGenerator, Dataset, Municipio, Partido (+6 more)

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.07
Nodes (25): ClassLoader, com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot (+17 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.05
Nodes (24): org.junit.jupiter.api.Test, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, VoteControllerTest.PortasEmMemoria, CastVoteCommand, CastVoteUseCaseTest, Rejected, RejectionReason (+16 more)

### Community 6 - "VotingProperties"
Cohesion: 0.15
Nodes (6): org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.kafka.core.KafkaTemplate, org.springframework.scheduling.annotation.Scheduled, VotingProperties, ControlEventPublisher, KafkaEventPublisher

### Community 7 - "testing.T"
Cohesion: 0.07
Nodes (66): pending, net/http/httptest.ResponseRecorder, sync.RWMutex, testing.T, NewServer(), cadeiaComJanelas(), contains(), get() (+58 more)

### Community 8 - "org.slf4j.Logger"
Cohesion: 0.12
Nodes (15): org.slf4j.Logger, org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException (+7 more)

### Community 9 - "Server"
Cohesion: 0.16
Nodes (14): errorResponse, ProofResponse, RootResponse, Server, net/http.Handler, net/http.Request, net/http.ResponseWriter, time.Time (+6 more)

### Community 10 - "Consumer"
Cohesion: 0.15
Nodes (12): context.Context, log/slog.Logger, time.Duration, kafka.Reader, kafka.Writer, Config, Consumer, Publisher (+4 more)

### Community 11 - "Sistema de Votação em Tempo Real — Kafka + Flink"
Cohesion: 0.07
Nodes (27): A base fixa, A correção: o tempo vira dado, A correção óbvia destruiria a auditoria, Arquitetura, Como o eleitor confere, Como rodar, Duas camadas, uma autoridade, Encerramento da votação (+19 more)

### Community 12 - "IngestApiApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling, IngestApiApplication

### Community 22 - "CastVoteUseCase"
Cohesion: 0.21
Nodes (10): FunctionalInterface, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, ReceiptPublisher, VoteEventPublisher, CastVoteUseCase, ReceiptPolicy, VotingConfiguration (+2 more)

### Community 23 - "VoteReceipt"
Cohesion: 0.09
Nodes (13): java.util.regex.Pattern, org.springframework.stereotype.Component, ReceiptEvent, VoteEventMapper, VoteEventMapperTest, Accepted, AdmissionDecision, Override (+5 more)

### Community 25 - "VoteAggregationJob.java"
Cohesion: 0.20
Nodes (10): org.apache.flink.api.common.ExecutionConfig, org.apache.flink.connector.kafka.source.KafkaSource, org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction, org.apache.flink.streaming.api.windowing.windows.TimeWindow, AcceptedVoteEvent, RejectedVoteEvent, WindowMarkerEvent, Context (+2 more)

### Community 26 - "TallyUpdateEvent"
Cohesion: 0.20
Nodes (8): org.apache.flink.api.common.state.ValueState, org.apache.flink.configuration.Configuration, org.apache.flink.streaming.api.functions.KeyedProcessFunction, TallyUpdateEvent, VoteTally, Context, Override, TallyProcessFunction

### Community 27 - "TallyDimension"
Cohesion: 0.21
Nodes (8): org.apache.flink.api.java.functions.KeySelector, TallyDimension, CANDIDATE, CITY, PARTY, STATE, DimensionKeySelector, Override

### Community 28 - "VoteCastEvent"
Cohesion: 0.27
Nodes (6): org.apache.flink.util.OutputTag, VoteCastEvent, DedupProcessFunction, Context, Counter, Override

### Community 31 - "VoteControllerClosedElectionTest.java"
Cohesion: 0.31
Nodes (6): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.test.web.servlet.MockMvc, VoteControllerClosedElectionTest.VotacaoEncerrada, VoteControllerClosedElectionTest

## Knowledge Gaps
- **39 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+34 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ElectionSchedule` connect `org.junit.jupiter.api.Test` to `Vote`, `JobConfig`, `VotingProperties`, `CastVoteUseCase`, `VoteReceipt`, `.voto`, `VoteAggregationJob.java`, `VoteCastEvent`, `VoteControllerClosedElectionTest.java`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **Why does `Vote` connect `Vote` to `Sha256ReceiptPolicy`, `org.junit.jupiter.api.Test`, `org.slf4j.Logger`, `CastVoteUseCase`, `VoteReceipt`, `.voto`, `TallyDimension`, `.toDomain`, `VoteControllerClosedElectionTest.java`?**
  _High betweenness centrality (0.054) - this node is a cross-community bridge._
- **Why does `VoteCastEvent` connect `VoteCastEvent` to `Vote`, `JsonTypeInfo`, `JobConfig`, `VoteReceipt`, `.voto`, `VoteAggregationJob.java`, `TallyUpdateEvent`, `TallyDimension`, `TimelineEvent`, `.toDomain`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `ElectionSchedule` (e.g. with `.oUltimoMilissegundoAntesDoPrazoAindaVale()` and `.recusaVotoAntesDaAbertura()`) actually correct?**
  _`ElectionSchedule` has 4 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _39 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `JobConfig` be split into smaller, more focused modules?**
  _Cohesion score 0.10897435897435898 - nodes in this community are weakly interconnected._
- **Should `VoteFeeder` be split into smaller, more focused modules?**
  _Cohesion score 0.07337526205450734 - nodes in this community are weakly interconnected._