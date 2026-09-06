package com.voting.streaming;

import com.voting.contracts.RejectedVoteEvent;
import com.voting.contracts.TallyUpdateEvent;
import com.voting.contracts.VoteCastEvent;
import com.voting.domain.tally.TallyDimension;
import com.voting.streaming.config.JobConfig;
import com.voting.streaming.dedup.DedupProcessFunction;
import com.voting.streaming.serde.JsonDeserializationSchema;
import com.voting.streaming.serde.JsonTypeInfo;
import com.voting.streaming.serde.KeyedJsonSerializationSchema;
import com.voting.streaming.tally.DimensionKeySelector;
import com.voting.streaming.tally.TallyProcessFunction;
import java.time.Duration;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apuracao em tempo real: le {@code votes.cast}, descarta votos repetidos e publica a
 * contagem corrente por candidato, estado, cidade e partido.
 *
 * <pre>
 *   votes.cast --keyBy(voterId)--> dedup --+--> keyBy(candidato) --> results.by-candidate
 *                                   |      +--> keyBy(estado)    --> results.by-state
 *                                   |      +--> keyBy(cidade)    --> results.by-city
 *                                   |      +--> keyBy(partido)   --> results.by-party
 *                                   +-- rejeitados --> votes.rejected
 * </pre>
 *
 * <p>O ponto do desenho: o dedup roda <em>uma vez</em>, antes do fan-out. As quatro contagens
 * enxergam exatamente o mesmo conjunto de votos admitidos e, por construcao, fecham entre si.
 */
public final class VoteAggregationJob {

    private static final Logger LOG = LoggerFactory.getLogger(VoteAggregationJob.class);

    private VoteAggregationJob() {
    }

    public static void main(String[] args) throws Exception {
        JobConfig config = JobConfig.fromArgs(args);
        LOG.info("iniciando apuracao com {}", config);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        configure(env, config);
        build(env, votesFrom(env, config), config);

        env.execute("apuracao-de-votos");
    }

    static void configure(StreamExecutionEnvironment env, JobConfig config) {
        env.setParallelism(config.parallelism());
        env.enableCheckpointing(config.checkpointIntervalMs(), CheckpointingMode.EXACTLY_ONCE);
    }

    private static DataStream<VoteCastEvent> votesFrom(StreamExecutionEnvironment env, JobConfig config) {
        KafkaSource<VoteCastEvent> source = KafkaSource.<VoteCastEvent>builder()
                .setBootstrapServers(config.bootstrapServers())
                .setTopics(config.votesTopic())
                .setGroupId(config.consumerGroup())
                // Uma apuracao precisa comecar do primeiro voto, e nao do momento em que o job
                // subiu; a partir dai os offsets do checkpoint mandam.
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(org.apache.flink.connector.kafka.source.reader.deserializer
                        .KafkaRecordDeserializationSchema.valueOnly(
                                new JsonDeserializationSchema<>(VoteCastEvent.class, JsonTypeInfo.VOTE_CAST)))
                .build();

        // Marca d'agua pelo horario do voto: nao muda nada nas contagens acumuladas de hoje,
        // mas e o que a agregacao por janela da proxima fase vai consumir.
        WatermarkStrategy<VoteCastEvent> watermarks = WatermarkStrategy
                .<VoteCastEvent>forBoundedOutOfOrderness(Duration.ofMillis(config.maxOutOfOrdernessMs()))
                .withTimestampAssigner((event, ts) -> event.castAt().toEpochMilli())
                .withIdleness(Duration.ofSeconds(30));

        return env.fromSource(source, watermarks, "votes.cast").uid("fonte-votos");
    }

    /**
     * Monta o pipeline sobre um fluxo de votos ja existente. Separado de {@link #main} para
     * que os testes exercitem exatamente esta topologia com uma fonte em memoria.
     */
    static SingleOutputStreamOperator<VoteCastEvent> build(
            StreamExecutionEnvironment env, DataStream<VoteCastEvent> votes, JobConfig config) {

        SingleOutputStreamOperator<VoteCastEvent> admitidos = dedup(votes, config.electionId());

        admitidos.getSideOutput(DedupProcessFunction.REJECTED)
                .sinkTo(rejectedSink(config))
                .name("sink-" + config.rejectedTopic())
                .uid("sink-rejeitados");

        for (TallyDimension dimension : TallyDimension.values()) {
            tally(admitidos, dimension)
                    .sinkTo(tallySink(config, dimension))
                    .name("sink-" + dimension.topic())
                    .uid("sink-" + dimension.name().toLowerCase());
        }

        return admitidos;
    }

    /**
     * Filtra o segundo voto de cada eleitor. Os recusados saem pela tag
     * {@link DedupProcessFunction#REJECTED}.
     */
    public static SingleOutputStreamOperator<VoteCastEvent> dedup(
            DataStream<VoteCastEvent> votes, String electionId) {
        return votes
                .keyBy(event -> event.voterId() == null ? "" : event.voterId())
                .process(new DedupProcessFunction(electionId))
                .returns(JsonTypeInfo.VOTE_CAST)
                .name("dedup-por-eleitor")
                .uid("dedup-por-eleitor");
    }

    /** Contagem corrente de uma dimensao sobre os votos ja admitidos. */
    public static SingleOutputStreamOperator<TallyUpdateEvent> tally(
            DataStream<VoteCastEvent> admitidos, TallyDimension dimension) {
        String nome = dimension.name().toLowerCase();
        return admitidos
                .keyBy(new DimensionKeySelector(dimension))
                .process(new TallyProcessFunction(dimension))
                .returns(JsonTypeInfo.TALLY)
                .name("apuracao-" + nome)
                .uid("apuracao-" + nome);
    }

    private static KafkaSink<TallyUpdateEvent> tallySink(JobConfig config, TallyDimension dimension) {
        return KafkaSink.<TallyUpdateEvent>builder()
                .setBootstrapServers(config.bootstrapServers())
                .setKafkaProducerConfig(config.producerProperties())
                .setDeliveryGuarantee(config.deliveryGuarantee())
                // Prefixo unico por sink: dois produtores transacionais nao podem compartilhar id.
                .setTransactionalIdPrefix("apuracao-" + dimension.name().toLowerCase())
                .setRecordSerializer(new KeyedJsonSerializationSchema<TallyUpdateEvent>(
                        dimension.topic(), TallyUpdateEvent::key))
                .build();
    }

    private static KafkaSink<RejectedVoteEvent> rejectedSink(JobConfig config) {
        return KafkaSink.<RejectedVoteEvent>builder()
                .setBootstrapServers(config.bootstrapServers())
                .setKafkaProducerConfig(config.producerProperties())
                .setDeliveryGuarantee(config.deliveryGuarantee())
                .setTransactionalIdPrefix("votos-rejeitados")
                .setRecordSerializer(new KeyedJsonSerializationSchema<RejectedVoteEvent>(
                        config.rejectedTopic(), RejectedVoteEvent::voterId))
                .build();
    }
}
