package com.voting.streaming;

import com.voting.contracts.AcceptedVoteEvent;
import com.voting.contracts.ControlEvent;
import com.voting.contracts.RejectedVoteEvent;
import com.voting.contracts.TallyUpdateEvent;
import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.VoteEventMapper;
import com.voting.contracts.WindowMarkerEvent;
import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.tally.TallyDimension;
import com.voting.streaming.config.JobConfig;
import com.voting.streaming.dedup.DedupProcessFunction;
import com.voting.streaming.timeline.TimelineEvent;
import com.voting.streaming.merkle.WindowMarkerFunction;
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
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apuracao em tempo real: le {@code votes.cast}, descarta votos repetidos e publica a
 * contagem corrente por candidato, estado, cidade e partido.
 *
 * <pre>
 *   votes.cast  ---+
 *   votes.control --+-- uniao --keyBy(voterId)--> dedup --+--> keyBy(candidato) --> results.by-candidate
 *                                                        |      +--> keyBy(estado)  --> results.by-state
 *                                                        |      +--> keyBy(cidade)  --> results.by-city
 *                                                        |      +--> keyBy(partido) --> results.by-party
 *                                                        |      +--> votes.accepted    (folhas da Merkle)
 *                                                        |      +--> janela --> votes.windows
 *                                                        +-- rejeitados --> votes.rejected
 * </pre>
 *
 * <p>{@code votes.control} nao traz votos: traz o tempo. A marca d'agua do Flink e derivada do
 * dado, entao um fluxo parado a congela e as janelas nunca fecham - os ultimos votos ficariam
 * sem raiz e sem prova de inclusao. Unindo os batimentos ao fluxo, o tempo de evento avanca
 * mesmo sem votos, e continua sendo tempo de evento: o replay reproduz as mesmas raizes.
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
        build(env, timelineFrom(env, config), config);

        env.execute("apuracao-de-votos");
    }

    static void configure(StreamExecutionEnvironment env, JobConfig config) {
        env.setParallelism(config.parallelism());
        env.enableCheckpointing(config.checkpointIntervalMs());
        // enableCheckpointing(long, CheckpointingMode) e o enum de streaming.api estao
        // depreciados no Flink 1.20 em favor de core.execution.CheckpointingMode configurado
        // pelo CheckpointConfig.
        env.getCheckpointConfig().setCheckpointingConsistencyMode(CheckpointingMode.EXACTLY_ONCE);
    }

    /**
     * Une votos e eventos de controle num fluxo so, com uma marca d'agua unica.
     *
     * <p>As fontes nao geram marca d'agua sozinhas ({@code noWatermarks}); ela e atribuida
     * depois da uniao. E o que garante que um batimento vindo de {@code votes.control} avance
     * o tempo mesmo quando nao ha um unico voto chegando.
     */
    private static DataStream<TimelineEvent> timelineFrom(StreamExecutionEnvironment env, JobConfig config) {
        DataStream<TimelineEvent> votos = env
                .fromSource(
                        kafkaSource(config, config.votesTopic(), VoteCastEvent.class, JsonTypeInfo.VOTE_CAST),
                        WatermarkStrategy.noWatermarks(),
                        config.votesTopic())
                .uid("fonte-votos")
                .map(TimelineEvent::ofVote)
                .returns(JsonTypeInfo.TIMELINE)
                .name("votos")
                .uid("envelope-votos");

        DataStream<TimelineEvent> controle = env
                .fromSource(
                        kafkaSource(config, config.controlTopic(), ControlEvent.class, JsonTypeInfo.CONTROL),
                        WatermarkStrategy.noWatermarks(),
                        config.controlTopic())
                .uid("fonte-controle")
                .map(TimelineEvent::ofControl)
                .returns(JsonTypeInfo.TIMELINE)
                .name("controle")
                .uid("envelope-controle");

        return votos.union(controle)
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<TimelineEvent>forBoundedOutOfOrderness(
                                Duration.ofMillis(config.maxOutOfOrdernessMs()))
                        .withTimestampAssigner((evento, ts) -> evento.eventTimeMillis())
                        .withIdleness(Duration.ofSeconds(30)))
                .name("linha-do-tempo")
                .uid("linha-do-tempo");
    }

    private static <T> KafkaSource<T> kafkaSource(
            JobConfig config, String topic, Class<T> tipo, org.apache.flink.api.common.typeinfo.TypeInformation<T> typeInfo) {
        return KafkaSource.<T>builder()
                .setBootstrapServers(config.bootstrapServers())
                .setTopics(topic)
                .setGroupId(config.consumerGroup())
                // Uma apuracao precisa comecar do primeiro voto, e nao do momento em que o job
                // subiu; a partir dai os offsets do checkpoint mandam.
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(org.apache.flink.connector.kafka.source.reader.deserializer
                        .KafkaRecordDeserializationSchema.valueOnly(
                                new JsonDeserializationSchema<>(tipo, typeInfo)))
                .build();
    }

    /**
     * Monta o pipeline sobre um fluxo de votos ja existente. Separado de {@link #main} para
     * que os testes exercitem exatamente esta topologia com uma fonte em memoria.
     */
    static SingleOutputStreamOperator<VoteCastEvent> build(
            StreamExecutionEnvironment env, DataStream<TimelineEvent> timeline, JobConfig config) {

        SingleOutputStreamOperator<VoteCastEvent> admitidos =
                dedup(votesOnly(timeline), config.electionSchedule());

        admitidos.getSideOutput(DedupProcessFunction.REJECTED)
                .sinkTo(rejectedSink(config))
                .name("sink-" + config.rejectedTopic())
                .uid("sink-rejeitados");

        merkleFeed(admitidos, config);

        for (TallyDimension dimension : TallyDimension.values()) {
            tally(admitidos, dimension)
                    .sinkTo(tallySink(config, dimension))
                    .name("sink-" + dimension.topic())
                    .uid("sink-" + dimension.name().toLowerCase());
        }

        return admitidos;
    }

    /**
     * Alimenta a Merkle Tree: as folhas em {@code votes.accepted} e o sinal de janela completa
     * em {@code votes.windows}.
     *
     * <p>Os dois topicos saem do mesmo fluxo pos-dedup, entao a arvore se compromete
     * exatamente com o conjunto que foi apurado - nem mais, nem menos.
     */
    private static void merkleFeed(DataStream<VoteCastEvent> admitidos, JobConfig config) {
        admitidos
                .map(voto -> VoteEventMapper.toAcceptedEvent(voto, config.merkleWindow()))
                .returns(JsonTypeInfo.ACCEPTED)
                .name("folhas-da-arvore")
                .uid("folhas-da-arvore")
                .sinkTo(sink(config, config.acceptedTopic(), "votos-aceitos", AcceptedVoteEvent::receipt))
                .name("sink-" + config.acceptedTopic())
                .uid("sink-aceitos");

        // windowAll roda com paralelismo 1: a contagem da janela e global por definicao, e nao
        // ha como somar as parciais sem um ponto de encontro. E so um contador, nao acumula votos.
        admitidos
                .windowAll(TumblingEventTimeWindows.of(config.merkleWindow()))
                .process(new WindowMarkerFunction())
                .returns(JsonTypeInfo.WINDOW_MARKER)
                .name("fechamento-de-janela")
                .uid("fechamento-de-janela")
                .sinkTo(sink(config, config.windowsTopic(), "janelas", WindowMarkerEvent::windowId))
                .name("sink-" + config.windowsTopic())
                .uid("sink-janelas");
    }

    /**
     * Descarta os eventos de controle depois de eles ja terem cumprido seu papel: existir no
     * fluxo com um horario, empurrando a marca d'agua.
     */
    public static DataStream<VoteCastEvent> votesOnly(DataStream<TimelineEvent> timeline) {
        return timeline
                .filter(TimelineEvent::hasVote)
                .name("descarta-controle")
                .uid("descarta-controle")
                .map(TimelineEvent::vote)
                .returns(JsonTypeInfo.VOTE_CAST)
                .name("votos-da-linha-do-tempo")
                .uid("votos-da-linha-do-tempo");
    }

    /**
     * Filtra o segundo voto de cada eleitor e os votos fora do periodo da eleicao. Os recusados
     * saem pela tag {@link DedupProcessFunction#REJECTED}.
     */
    public static SingleOutputStreamOperator<VoteCastEvent> dedup(
            DataStream<VoteCastEvent> votes, ElectionSchedule schedule) {
        return votes
                .keyBy(event -> event.voterId() == null ? "" : event.voterId())
                .process(new DedupProcessFunction(schedule))
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
        return sink(config, dimension.topic(), "apuracao-" + dimension.name().toLowerCase(),
                TallyUpdateEvent::key);
    }

    private static KafkaSink<RejectedVoteEvent> rejectedSink(JobConfig config) {
        return sink(config, config.rejectedTopic(), "votos-rejeitados", RejectedVoteEvent::voterId);
    }

    /**
     * Sink Kafka transacional com chave explicita.
     *
     * @param transactionalIdPrefix precisa ser unico por sink - dois produtores transacionais
     *                              nao podem compartilhar o mesmo id
     */
    private static <T> KafkaSink<T> sink(
            JobConfig config,
            String topic,
            String transactionalIdPrefix,
            KeyedJsonSerializationSchema.KeyExtractor<T> chave) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(config.bootstrapServers())
                .setKafkaProducerConfig(config.producerProperties())
                .setDeliveryGuarantee(config.deliveryGuarantee())
                .setTransactionalIdPrefix(transactionalIdPrefix)
                .setRecordSerializer(new KeyedJsonSerializationSchema<>(topic, chave))
                .build();
    }
}
