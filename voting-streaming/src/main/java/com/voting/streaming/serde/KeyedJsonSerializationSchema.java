package com.voting.streaming.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.contracts.EventJson;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Escreve eventos JSON no Kafka com uma chave explicita.
 *
 * <p>A chave nao e decorativa: nos topicos {@code results.by-*} ela e o que a compactacao do
 * Kafka usa para manter apenas a contagem mais recente de cada candidato, estado, cidade ou
 * partido. Um topico de resultados sem chave cresceria para sempre e nao seria reconstruivel.
 */
public final class KeyedJsonSerializationSchema<T> implements KafkaRecordSerializationSchema<T> {

    private static final long serialVersionUID = 1L;

    /** Extrator de chave serializavel - o Flink envia esta funcao para os TaskManagers. */
    public interface KeyExtractor<T> extends Function<T, String>, Serializable {
    }

    private final String topic;
    private final KeyExtractor<T> keyExtractor;
    private transient ObjectMapper mapper;

    public KeyedJsonSerializationSchema(String topic, KeyExtractor<T> keyExtractor) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor");
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(T element, KafkaSinkContext context, Long timestamp) {
        try {
            byte[] key = keyExtractor.apply(element).getBytes(StandardCharsets.UTF_8);
            return new ProducerRecord<>(topic, null, timestamp, key, mapper().writeValueAsBytes(element));
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar evento para o topico " + topic, e);
        }
    }

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = EventJson.create();
        }
        return mapper;
    }
}
