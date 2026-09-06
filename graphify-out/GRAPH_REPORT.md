# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 106 files · ~29,317 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 747 nodes · 2011 edges · 32 communities (22 shown, 10 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 143 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `d15bd68d`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ElectionSchedule
- VoteAggregationJob.java
- JobConfig
- DatasetTest.java
- com.fasterxml.jackson.databind.ObjectMapper
- org.junit.jupiter.api.Test
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
- VoteTally
- .toDomain
- ElectionId
- org.springframework.context.annotation.Configuration
- RejectionReason
- VoteFeeder

## God Nodes (most connected - your core abstractions)
1. `ElectionSchedule` - 50 edges
2. `Vote` - 46 edges
3. `VoteCastEvent` - 34 edges
4. `VoteReceipt` - 32 edges
5. `ElectionId` - 31 edges
6. `JobConfig` - 28 edges
7. `JsonTypeInfo` - 25 edges
8. `CastVoteUseCase` - 20 edges
9. `Log` - 20 edges
10. `NewLog()` - 20 edges

## Surprising Connections (you probably didn't know these)
- `CastVoteResult` --references--> `Vote`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/model/Vote.java
- `CastVoteResult` --references--> `VoteReceipt`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/receipt/VoteReceipt.java
- `CastVoteUseCase` --references--> `ElectionSchedule`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteUseCase.java → voting-domain/src/main/java/com/voting/domain/election/ElectionSchedule.java
- `CastVoteUseCase` --references--> `ReceiptPolicy`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteUseCase.java → voting-domain/src/main/java/com/voting/domain/receipt/ReceiptPolicy.java
- `CastVoteUseCaseTest` --references--> `ElectionId`  [EXTRACTED]
  voting-application/src/test/java/com/voting/application/usecase/CastVoteUseCaseTest.java → voting-domain/src/main/java/com/voting/domain/model/ElectionId.java

## Import Cycles
- None detected.

## Communities (32 total, 10 thin omitted)

### Community 0 - "ElectionSchedule"
Cohesion: 0.16
Nodes (9): Accepted, AdmissionDecision, Rejected, VoteAdmission, ElectionSchedule, VoteAdmissionTest, Sha256ReceiptPolicyTest, Context (+1 more)

### Community 1 - "VoteAggregationJob.java"
Cohesion: 0.06
Nodes (33): Deprecated, org.apache.flink.api.common.ExecutionConfig, org.apache.flink.api.common.state.ValueState, org.apache.flink.api.java.functions.KeySelector, org.apache.flink.configuration.Configuration, org.apache.flink.streaming.api.functions.KeyedProcessFunction, org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction, org.apache.flink.streaming.api.windowing.windows.TimeWindow (+25 more)

### Community 2 - "JobConfig"
Cohesion: 0.09
Nodes (13): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.connector.kafka.source.KafkaSource, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, TallyUpdateEvent (+5 more)

### Community 3 - "DatasetTest.java"
Cohesion: 0.15
Nodes (6): Candidato, CandidatosGenerator, Dataset, Municipio, Partido, DatasetTest

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.07
Nodes (26): ClassLoader, com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot (+18 more)

### Community 5 - "org.junit.jupiter.api.Test"
Cohesion: 0.07
Nodes (16): org.junit.jupiter.api.Test, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, VoteControllerTest.PortasEmMemoria, CastVoteCommand, CastVoteResult, CastVoteUseCase (+8 more)

### Community 6 - "VotingProperties"
Cohesion: 0.11
Nodes (9): com.fasterxml.jackson.databind.JsonNode, org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.kafka.core.KafkaTemplate, org.springframework.scheduling.annotation.Scheduled, VotingProperties, ControlEventPublisher, KafkaEventPublisher, ControlEventPublisherTest (+1 more)

### Community 7 - "testing.T"
Cohesion: 0.06
Nodes (72): pending, net/http/httptest.ResponseRecorder, sync.RWMutex, testing.B, testing.T, NewServer(), cadeiaComJanelas(), contains() (+64 more)

### Community 8 - "DomainException"
Cohesion: 0.10
Nodes (12): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException, DomainException, ElectionClosedException, InvalidElectionScheduleException, InvalidTallyException (+4 more)

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

### Community 22 - "Vote"
Cohesion: 0.17
Nodes (9): CandidateId, Override, Override, PartyId, Region, Vote, Override, VoterId (+1 more)

### Community 23 - "ReceiptPublisher"
Cohesion: 0.13
Nodes (11): FunctionalInterface, org.springframework.context.annotation.Bean, org.springframework.stereotype.Component, ReceiptPublisher, VoteEventPublisher, ReceiptPolicy, VotingConfiguration, KafkaReceiptPublisher (+3 more)

### Community 24 - "VoteReceipt"
Cohesion: 0.11
Nodes (8): java.util.regex.Pattern, ReceiptEvent, VoteEventMapper, VoteEventMapperTest, Override, VoteReceipt, Override, Override

### Community 25 - "VoteControllerClosedElectionTest.java"
Cohesion: 0.20
Nodes (8): org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Import, org.springframework.test.web.servlet.MockMvc, VoteControllerClosedElectionTest.VotacaoEncerrada, Override, Sha256ReceiptPolicy, VoteControllerClosedElectionTest

### Community 26 - "VoteTally"
Cohesion: 0.17
Nodes (5): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, VoteTally, Votes, VoteTallyTest

### Community 28 - "ElectionId"
Cohesion: 0.36
Nodes (3): WrongElectionException, ElectionId, Override

### Community 29 - "org.springframework.context.annotation.Configuration"
Cohesion: 0.43
Nodes (5): org.springframework.context.annotation.Configuration, org.springframework.web.servlet.config.annotation.PathMatchConfigurer, org.springframework.web.servlet.config.annotation.WebMvcConfigurer, ApiVersionConfiguration, Override

### Community 32 - "RejectionReason"
Cohesion: 0.29
Nodes (6): RejectionReason, DUPLICATE_VOTE, ELECTION_CLOSED, ELECTION_NOT_OPEN, INVALID_VOTE, WRONG_ELECTION

### Community 34 - "VoteFeeder"
Cohesion: 0.14
Nodes (8): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Override, VoteFeeder, VoteFeederTest, Override, VoteSimulation

## Knowledge Gaps
- **40 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+35 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **10 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ElectionSchedule` connect `ElectionSchedule` to `VoteAggregationJob.java`, `JobConfig`, `org.junit.jupiter.api.Test`, `VotingProperties`, `DomainException`, `Vote`, `ReceiptPublisher`, `VoteReceipt`, `VoteControllerClosedElectionTest.java`, `ElectionId`?**
  _High betweenness centrality (0.074) - this node is a cross-community bridge._
- **Why does `Vote` connect `Vote` to `ElectionSchedule`, `VoteAggregationJob.java`, `JobConfig`, `org.junit.jupiter.api.Test`, `ReceiptPublisher`, `VoteReceipt`, `VoteControllerClosedElectionTest.java`, `VoteTally`, `.toDomain`, `ElectionId`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Why does `ElectionId` connect `ElectionId` to `ElectionSchedule`, `JobConfig`, `org.junit.jupiter.api.Test`, `VotingProperties`, `Vote`, `ReceiptPublisher`, `VoteReceipt`, `VoteControllerClosedElectionTest.java`, `VoteTally`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `ElectionSchedule` (e.g. with `.oUltimoMilissegundoAntesDoPrazoAindaVale()` and `.recusaVotoAntesDaAbertura()`) actually correct?**
  _`ElectionSchedule` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _40 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `VoteAggregationJob.java` be split into smaller, more focused modules?**
  _Cohesion score 0.05711849957374254 - nodes in this community are weakly interconnected._
- **Should `JobConfig` be split into smaller, more focused modules?**
  _Cohesion score 0.09491525423728814 - nodes in this community are weakly interconnected._