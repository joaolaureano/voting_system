# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 99 files · ~27,207 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 708 nodes · 1912 edges · 35 communities (25 shown, 10 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 138 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `79a2f6a6`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- VoteEventMapper.java
- JsonTypeInfo
- JobConfig
- DatasetTest.java
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
- Vote
- .voto
- VoteAggregationJob.java
- TallyUpdateEvent
- TallyDimension
- DedupProcessFunction
- VoteCastEvent
- VoteEventMapper
- VoteControllerClosedElectionTest.java
- .of
- ElectionSchedule
- VoteSimulation

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
- `CastVoteUseCase` --references--> `ElectionSchedule`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteUseCase.java → voting-domain/src/main/java/com/voting/domain/election/ElectionSchedule.java

## Import Cycles
- None detected.

## Communities (35 total, 10 thin omitted)

### Community 0 - "VoteEventMapper.java"
Cohesion: 0.12
Nodes (8): CandidateId, Override, Override, PartyId, Region, Override, VoterId, VoteWindow

### Community 1 - "JsonTypeInfo"
Cohesion: 0.22
Nodes (3): org.apache.flink.api.common.ExecutionConfig, Override, JsonTypeInfo

### Community 2 - "JobConfig"
Cohesion: 0.11
Nodes (10): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.connector.kafka.source.KafkaSource, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, Override, JobConfig (+2 more)

### Community 3 - "DatasetTest.java"
Cohesion: 0.15
Nodes (6): Candidato, CandidatosGenerator, Dataset, Municipio, Partido, DatasetTest

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.07
Nodes (25): ClassLoader, com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot (+17 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.07
Nodes (10): org.junit.jupiter.api.Test, VoteControllerTest.PortasEmMemoria, CastVoteCommand, CastVoteUseCaseTest, Override, VoteFeeder, VoteFeederTest, ElectionScheduleTest (+2 more)

### Community 6 - "VotingProperties"
Cohesion: 0.17
Nodes (10): org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.kafka.core.KafkaTemplate, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, VotingProperties, ControlEventPublisher, KafkaEventPublisher, KafkaReceiptPublisher (+2 more)

### Community 7 - "testing.T"
Cohesion: 0.06
Nodes (72): pending, net/http/httptest.ResponseRecorder, sync.RWMutex, testing.B, testing.T, NewServer(), cadeiaComJanelas(), contains() (+64 more)

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
Nodes (28): A base fixa, A correção: o tempo vira dado, A correção óbvia destruiria a auditoria, Arquitetura, Como o eleitor confere, Como rodar, Desempenho, Duas camadas, uma autoridade (+20 more)

### Community 12 - "IngestApiApplication"
Cohesion: 0.53
Nodes (4): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling, IngestApiApplication

### Community 22 - "CastVoteUseCase"
Cohesion: 0.22
Nodes (10): FunctionalInterface, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, ReceiptPublisher, VoteEventPublisher, CastVoteUseCase, ReceiptPolicy, VotingConfiguration (+2 more)

### Community 23 - "Vote"
Cohesion: 0.13
Nodes (7): java.util.regex.Pattern, ReceiptEvent, Vote, Override, VoteReceipt, keyOf(), Override

### Community 24 - ".voto"
Cohesion: 0.21
Nodes (3): org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator, VoteTest, VoteAggregationPipelineTest

### Community 25 - "VoteAggregationJob.java"
Cohesion: 0.23
Nodes (8): org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction, org.apache.flink.streaming.api.windowing.windows.TimeWindow, AcceptedVoteEvent, RejectedVoteEvent, WindowMarkerEvent, Context, Override, WindowMarkerFunction

### Community 26 - "TallyUpdateEvent"
Cohesion: 0.21
Nodes (7): org.apache.flink.api.common.state.ValueState, org.apache.flink.streaming.api.functions.KeyedProcessFunction, TallyUpdateEvent, VoteTally, Context, Override, TallyProcessFunction

### Community 27 - "TallyDimension"
Cohesion: 0.21
Nodes (8): org.apache.flink.api.java.functions.KeySelector, TallyDimension, CANDIDATE, CITY, PARTY, STATE, DimensionKeySelector, Override

### Community 28 - "DedupProcessFunction"
Cohesion: 0.23
Nodes (6): org.apache.flink.configuration.Configuration, org.apache.flink.util.OutputTag, DedupProcessFunction, Context, Counter, Override

### Community 29 - "VoteCastEvent"
Cohesion: 0.29
Nodes (3): ControlEvent, VoteCastEvent, TimelineEvent

### Community 31 - "VoteControllerClosedElectionTest.java"
Cohesion: 0.24
Nodes (8): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.test.web.servlet.MockMvc, VoteControllerClosedElectionTest.VotacaoEncerrada, Override, Sha256ReceiptPolicy, VoteControllerClosedElectionTest

### Community 32 - ".of"
Cohesion: 0.11
Nodes (16): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, Accepted, AdmissionDecision, Rejected, RejectionReason, DUPLICATE_VOTE, ELECTION_CLOSED (+8 more)

### Community 33 - "ElectionSchedule"
Cohesion: 0.21
Nodes (4): ElectionClosedException, ElectionSchedule, ElectionId, Override

### Community 34 - "VoteSimulation"
Cohesion: 0.36
Nodes (5): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Override, VoteSimulation

## Knowledge Gaps
- **40 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+35 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **10 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ElectionSchedule` connect `ElectionSchedule` to `.of`, `JobConfig`, `org.junit.jupiter.api.Test`, `VotingProperties`, `CastVoteUseCase`, `Vote`, `.voto`, `VoteAggregationJob.java`, `DedupProcessFunction`, `VoteControllerClosedElectionTest.java`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **Why does `Vote` connect `Vote` to `VoteEventMapper.java`, `ElectionSchedule`, `.of`, `org.junit.jupiter.api.Test`, `VotingProperties`, `org.slf4j.Logger`, `CastVoteUseCase`, `.voto`, `VoteAggregationJob.java`, `TallyDimension`, `VoteEventMapper`, `VoteControllerClosedElectionTest.java`?**
  _High betweenness centrality (0.052) - this node is a cross-community bridge._
- **Why does `VoteCastEvent` connect `VoteCastEvent` to `JsonTypeInfo`, `JobConfig`, `ElectionSchedule`, `.voto`, `VoteAggregationJob.java`, `TallyUpdateEvent`, `TallyDimension`, `DedupProcessFunction`, `VoteEventMapper`?**
  _High betweenness centrality (0.036) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `ElectionSchedule` (e.g. with `.oUltimoMilissegundoAntesDoPrazoAindaVale()` and `.recusaVotoAntesDaAbertura()`) actually correct?**
  _`ElectionSchedule` has 4 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _40 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `VoteEventMapper.java` be split into smaller, more focused modules?**
  _Cohesion score 0.12105263157894737 - nodes in this community are weakly interconnected._
- **Should `JobConfig` be split into smaller, more focused modules?**
  _Cohesion score 0.10897435897435898 - nodes in this community are weakly interconnected._