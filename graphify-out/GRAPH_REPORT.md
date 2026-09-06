# Graph Report - voting_system  (2026-09-06)

## Corpus Check
- 72 files · ~13,138 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 452 nodes · 1181 edges · 21 communities (13 shown, 8 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 57 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Vote
- VoteCastEvent
- JobConfig
- org.junit.jupiter.api.Test
- com.fasterxml.jackson.databind.ObjectMapper
- .of
- ApiExceptionHandler.java
- DatasetTest.java
- JsonTypeSerializer
- VoteSimulation
- JsonTypeInfo
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

## God Nodes (most connected - your core abstractions)
1. `Vote` - 44 edges
2. `VoteReceipt` - 32 edges
3. `VoteCastEvent` - 26 edges
4. `ElectionId` - 23 edges
5. `JsonTypeInfo` - 21 edges
6. `JobConfig` - 20 edges
7. `TallyDimension` - 19 edges
8. `VoteFeeder` - 18 edges
9. `CastVoteUseCaseTest` - 17 edges
10. `ReceiptPolicy` - 17 edges

## Surprising Connections (you probably didn't know these)
- `CastVoteResult` --references--> `Vote`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/model/Vote.java
- `CastVoteResult` --references--> `VoteReceipt`  [EXTRACTED]
  voting-application/src/main/java/com/voting/application/usecase/CastVoteResult.java → voting-domain/src/main/java/com/voting/domain/receipt/VoteReceipt.java
- `VoteController` --references--> `CastVoteUseCase`  [EXTRACTED]
  voting-ingest-api/src/main/java/com/voting/ingest/web/VoteController.java → voting-application/src/main/java/com/voting/application/usecase/CastVoteUseCase.java
- `JsonTypeInfo` --references--> `RejectedVoteEvent`  [EXTRACTED]
  voting-streaming/src/main/java/com/voting/streaming/serde/JsonTypeInfo.java → voting-contracts/src/main/java/com/voting/contracts/RejectedVoteEvent.java
- `JsonTypeInfo` --references--> `TallyUpdateEvent`  [EXTRACTED]
  voting-streaming/src/main/java/com/voting/streaming/serde/JsonTypeInfo.java → voting-contracts/src/main/java/com/voting/contracts/TallyUpdateEvent.java

## Import Cycles
- None detected.

## Communities (21 total, 8 thin omitted)

### Community 0 - "Vote"
Cohesion: 0.06
Nodes (36): FunctionalInterface, java.util.regex.Pattern, org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.boot.test.context.TestConfiguration, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.kafka.core.KafkaTemplate, org.springframework.stereotype.Component (+28 more)

### Community 1 - "VoteCastEvent"
Cohesion: 0.06
Nodes (32): org.apache.flink.api.common.state.ValueState, org.apache.flink.api.java.functions.KeySelector, org.apache.flink.configuration.Configuration, org.apache.flink.streaming.api.functions.KeyedProcessFunction, org.apache.flink.util.OutputTag, org.slf4j.Logger, ReceiptEvent, RejectedVoteEvent (+24 more)

### Community 2 - "JobConfig"
Cohesion: 0.12
Nodes (10): org.apache.flink.api.java.utils.ParameterTool, org.apache.flink.connector.base.DeliveryGuarantee, org.apache.flink.connector.kafka.sink.KafkaSink, org.apache.flink.streaming.api.datastream.DataStream, org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator, org.apache.flink.streaming.api.environment.StreamExecutionEnvironment, Override, JobConfig (+2 more)

### Community 3 - "org.junit.jupiter.api.Test"
Cohesion: 0.07
Nodes (14): org.junit.jupiter.api.Test, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.context.annotation.Import, org.springframework.test.web.servlet.MockMvc, VoteControllerTest.PortasEmMemoria, CastVoteCommand, CastVoteResult, Override (+6 more)

### Community 4 - "com.fasterxml.jackson.databind.ObjectMapper"
Cohesion: 0.12
Nodes (16): com.fasterxml.jackson.databind.ObjectMapper, InitializationContext, KafkaSinkContext, org.apache.flink.api.common.serialization.DeserializationSchema, org.apache.flink.api.common.typeinfo.TypeInformation, org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema, org.apache.kafka.clients.producer.ProducerRecord, ProducerRecord (+8 more)

### Community 5 - ".of"
Cohesion: 0.13
Nodes (9): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource, Accepted, AdmissionDecision, VoteAdmission, VoteAdmissionTest, Votes, Sha256ReceiptPolicyTest (+1 more)

### Community 6 - "ApiExceptionHandler.java"
Cohesion: 0.17
Nodes (11): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException, EventPublicationException (+3 more)

### Community 7 - "DatasetTest.java"
Cohesion: 0.15
Nodes (6): Candidato, CandidatosGenerator, Dataset, Municipio, Partido, DatasetTest

### Community 8 - "JsonTypeSerializer"
Cohesion: 0.16
Nodes (9): ClassLoader, org.apache.flink.api.common.typeutils.TypeSerializer, org.apache.flink.api.common.typeutils.TypeSerializerSnapshot, org.apache.flink.core.memory.DataInputView, org.apache.flink.core.memory.DataOutputView, SuppressWarnings, Override, JsonTypeSerializer (+1 more)

### Community 9 - "VoteSimulation"
Cohesion: 0.36
Nodes (5): io.gatling.javaapi.core.ScenarioBuilder, io.gatling.javaapi.core.Simulation, io.gatling.javaapi.http.HttpProtocolBuilder, Override, VoteSimulation

### Community 10 - "JsonTypeInfo"
Cohesion: 0.22
Nodes (3): org.apache.flink.api.common.ExecutionConfig, Override, JsonTypeInfo

### Community 11 - "Sistema de Votação em Tempo Real — Kafka + Flink"
Cohesion: 0.15
Nodes (12): A base fixa, Arquitetura, Como rodar, Garantias, Nota de privacidade sobre o recibo, Notas de implementação, O que é determinístico, e o que não é, Próximas fases (+4 more)

### Community 12 - "IngestApiApplication"
Cohesion: 0.60
Nodes (3): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, IngestApiApplication

## Knowledge Gaps
- **23 isolated node(s):** `com.voting:voting-system`, `voting-application`, `voting-benchmark`, `voting-contracts`, `voting-domain` (+18 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Vote` connect `Vote` to `VoteCastEvent`, `JobConfig`, `org.junit.jupiter.api.Test`, `.of`?**
  _High betweenness centrality (0.116) - this node is a cross-community bridge._
- **Why does `VoteCastEvent` connect `VoteCastEvent` to `Vote`, `JsonTypeInfo`, `JobConfig`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `JsonTypeInfo` connect `JsonTypeInfo` to `Vote`, `VoteCastEvent`, `com.fasterxml.jackson.databind.ObjectMapper`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **What connects `com.voting:voting-system`, `voting-application`, `voting-benchmark` to the rest of the system?**
  _23 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Vote` be split into smaller, more focused modules?**
  _Cohesion score 0.062173458725182866 - nodes in this community are weakly interconnected._
- **Should `VoteCastEvent` be split into smaller, more focused modules?**
  _Cohesion score 0.06180733162830349 - nodes in this community are weakly interconnected._
- **Should `JobConfig` be split into smaller, more focused modules?**
  _Cohesion score 0.12473572938689217 - nodes in this community are weakly interconnected._