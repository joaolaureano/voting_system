package com.voting.streaming.config;

import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.ElectionId;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;

/**
 * Parametros do job, com defaults de ambiente local. Sobrescreva com {@code --chave valor} na
 * submissao.
 *
 * <p>Os parametros estao agrupados por quem responde por eles, porque sao pessoas diferentes:
 * os de <strong>Kafka</strong> e os de <strong>runtime do Flink</strong> pertencem a operacao,
 * e mudam com o ambiente; os de <strong>negocio</strong> pertencem a quem organiza a eleicao,
 * e mudam com o pleito. Misturar os tres numa lista unica faz um ajuste de paralelismo parecer
 * do mesmo naipe que a data de encerramento.
 *
 * <p>Note o que <em>nao</em> esta aqui: nenhuma regra. Endereco, credencial e ajuste
 * operacional entram nesta classe; a decisao sobre o que conta como voto valido vive no
 * dominio.
 */
public final class JobConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------------------------------------------------------- Kafka: onde e o que ler
    private final String bootstrapServers;
    private final String consumerGroup;
    private final String votesTopic;
    private final String controlTopic;
    private final String rejectedTopic;
    private final String acceptedTopic;
    private final String windowsTopic;
    private final long transactionTimeoutMs;

    // ------------------------------------------------- Runtime do Flink: como o job se comporta
    private final int parallelism;
    private final long checkpointIntervalMs;
    private final long maxOutOfOrdernessMs;
    private final boolean exactlyOnce;

    // ------------------------------------------------------------ Negocio: qual eleicao e quando
    private final String electionId;
    private final String electionOpensAt;
    private final String electionClosesAt;
    private final long merkleWindowMs;

    private JobConfig(ParameterTool p) {
        // Kafka
        this.bootstrapServers = p.get("bootstrap.servers", "localhost:9092");
        this.consumerGroup = p.get("consumer.group", "voting-aggregation");
        this.votesTopic = p.get("topic.votes", "votes.cast");
        this.controlTopic = p.get("topic.control", "votes.control");
        this.rejectedTopic = p.get("topic.rejected", "votes.rejected");
        this.acceptedTopic = p.get("topic.accepted", "votes.accepted");
        this.windowsTopic = p.get("topic.windows", "votes.windows");
        this.transactionTimeoutMs = p.getLong("transaction.timeout.ms", 900_000L);

        // Runtime do Flink
        this.parallelism = p.getInt("parallelism", 2);
        this.checkpointIntervalMs = p.getLong("checkpoint.interval.ms", 10_000L);
        this.maxOutOfOrdernessMs = p.getLong("watermark.out.of.orderness.ms", 5_000L);
        this.exactlyOnce = p.getBoolean("exactly.once", true);

        // Negocio
        this.electionId = p.get("election.id", "br-2026-presidencial");
        this.electionOpensAt = p.get("election.opens.at", "");
        this.electionClosesAt = p.get("election.closes.at", "");
        this.merkleWindowMs = p.getLong("merkle.window.ms", 60_000L);
    }

    public static JobConfig fromArgs(String[] args) {
        return new JobConfig(ParameterTool.fromArgs(args));
    }

    // =========================================================================== Kafka

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public String consumerGroup() {
        return consumerGroup;
    }

    public String votesTopic() {
        return votesTopic;
    }

    public String controlTopic() {
        return controlTopic;
    }

    public String rejectedTopic() {
        return rejectedTopic;
    }

    public String acceptedTopic() {
        return acceptedTopic;
    }

    public String windowsTopic() {
        return windowsTopic;
    }

    /** Propriedades do produtor dos sinks. */
    public Properties producerProperties() {
        Properties props = new Properties();
        // Precisa ser menor ou igual ao transaction.max.timeout.ms do broker, senao o
        // produtor transacional e recusado na inicializacao do sink.
        props.setProperty("transaction.timeout.ms", Long.toString(transactionTimeoutMs));
        return props;
    }

    // ================================================================= Runtime do Flink

    public int parallelism() {
        return parallelism;
    }

    public long checkpointIntervalMs() {
        return checkpointIntervalMs;
    }

    /**
     * Folga aceita para eventos fora de ordem.
     *
     * <p>A margem da sentinela de encerramento, na API de ingestao, precisa exceder este valor:
     * a marca d'agua e {@code maior_horario_visto - este_valor}, e uma sentinela mais proxima
     * que isso nao ultrapassaria a ultima janela.
     */
    public long maxOutOfOrdernessMs() {
        return maxOutOfOrdernessMs;
    }

    /**
     * Exactly-once e o default porque as contagens sao acumulativas: com at-least-once, um
     * restart reprocessa votos ja contados e o placar infla - um erro visivel e sem correcao
     * automatica. Custa latencia (os resultados so aparecem ao fim de cada checkpoint).
     */
    public DeliveryGuarantee deliveryGuarantee() {
        return exactlyOnce ? DeliveryGuarantee.EXACTLY_ONCE : DeliveryGuarantee.AT_LEAST_ONCE;
    }

    // ========================================================================= Negocio

    public String electionId() {
        return electionId;
    }

    /**
     * Periodo da eleicao. Sem os dois parametros, a eleicao nao tem prazo - conveniente em
     * desenvolvimento, e um erro de operacao em producao.
     */
    public ElectionSchedule electionSchedule() {
        ElectionId id = ElectionId.of(electionId);
        if (electionOpensAt.isBlank() || electionClosesAt.isBlank()) {
            return ElectionSchedule.alwaysOpen(id);
        }
        return new ElectionSchedule(id, Instant.parse(electionOpensAt), Instant.parse(electionClosesAt));
    }

    /**
     * Tamanho da janela que a Merkle Tree sela. Janelas curtas dao confirmacao mais rapida ao
     * eleitor e arvores menores; janelas longas dao menos raizes para auditar. O servico Go
     * nao precisa conhecer este valor - ele recebe o windowId pronto.
     */
    public Duration merkleWindow() {
        return Duration.ofMillis(merkleWindowMs);
    }

    @Override
    public String toString() {
        return "JobConfig{bootstrap=" + bootstrapServers
                + ", votes=" + votesTopic
                + ", election=" + electionId
                + ", periodo=" + (electionOpensAt.isBlank() ? "sem prazo" : electionOpensAt + ".." + electionClosesAt)
                + ", janelaMerkle=" + merkleWindow()
                + ", guarantee=" + deliveryGuarantee()
                + ", parallelism=" + parallelism + '}';
    }
}
