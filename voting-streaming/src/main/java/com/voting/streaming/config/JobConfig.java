package com.voting.streaming.config;

import java.io.Serializable;
import java.util.Properties;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;

/**
 * Parametros do job, com defaults de ambiente local.
 *
 * <p>Tudo que e endereco, credencial ou ajuste operacional entra aqui - nenhuma dessas coisas
 * aparece no dominio. Sobrescreva com {@code --chave valor} na submissao.
 */
public final class JobConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String bootstrapServers;
    private final String votesTopic;
    private final String rejectedTopic;
    private final String consumerGroup;
    private final String electionId;
    private final long checkpointIntervalMs;
    private final long maxOutOfOrdernessMs;
    private final long transactionTimeoutMs;
    private final boolean exactlyOnce;
    private final int parallelism;

    private JobConfig(ParameterTool p) {
        this.bootstrapServers = p.get("bootstrap.servers", "localhost:9092");
        this.votesTopic = p.get("topic.votes", "votes.cast");
        this.rejectedTopic = p.get("topic.rejected", "votes.rejected");
        this.consumerGroup = p.get("consumer.group", "voting-aggregation");
        this.electionId = p.get("election.id", "br-2026-presidencial");
        this.checkpointIntervalMs = p.getLong("checkpoint.interval.ms", 10_000L);
        this.maxOutOfOrdernessMs = p.getLong("watermark.out.of.orderness.ms", 5_000L);
        this.transactionTimeoutMs = p.getLong("transaction.timeout.ms", 900_000L);
        this.exactlyOnce = p.getBoolean("exactly.once", true);
        this.parallelism = p.getInt("parallelism", 2);
    }

    public static JobConfig fromArgs(String[] args) {
        return new JobConfig(ParameterTool.fromArgs(args));
    }

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public String votesTopic() {
        return votesTopic;
    }

    public String rejectedTopic() {
        return rejectedTopic;
    }

    public String consumerGroup() {
        return consumerGroup;
    }

    public String electionId() {
        return electionId;
    }

    public long checkpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public long maxOutOfOrdernessMs() {
        return maxOutOfOrdernessMs;
    }

    public int parallelism() {
        return parallelism;
    }

    /**
     * Exactly-once e o default porque as contagens sao acumulativas: com at-least-once, um
     * restart reprocessa votos ja contados e o placar infla - um erro visivel e sem correcao
     * automatica. Custa latencia (os resultados so aparecem ao fim de cada checkpoint).
     */
    public DeliveryGuarantee deliveryGuarantee() {
        return exactlyOnce ? DeliveryGuarantee.EXACTLY_ONCE : DeliveryGuarantee.AT_LEAST_ONCE;
    }

    /** Propriedades do produtor dos sinks. */
    public Properties producerProperties() {
        Properties props = new Properties();
        // Precisa ser menor ou igual ao transaction.max.timeout.ms do broker, senao o
        // produtor transacional e recusado na inicializacao do sink.
        props.setProperty("transaction.timeout.ms", Long.toString(transactionTimeoutMs));
        return props;
    }

    @Override
    public String toString() {
        return "JobConfig{bootstrap=" + bootstrapServers
                + ", votes=" + votesTopic
                + ", election=" + electionId
                + ", guarantee=" + deliveryGuarantee()
                + ", parallelism=" + parallelism + '}';
    }
}
