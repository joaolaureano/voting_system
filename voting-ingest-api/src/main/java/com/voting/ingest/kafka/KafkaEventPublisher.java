package com.voting.ingest.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.contracts.EventJson;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Base dos adaptadores de saida para o Kafka.
 *
 * <p>A publicacao e sincrona de proposito. Responder 201 antes do ack do broker seria
 * prometer ao eleitor que o voto entrou quando ele ainda pode se perder - exatamente o tipo
 * de mentira que um comprovante de voto nao pode conter.
 */
public abstract class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final long timeoutMs;
    private final ObjectMapper json = EventJson.mapper();

    protected KafkaEventPublisher(KafkaTemplate<String, String> kafka, long timeoutMs) {
        this.kafka = kafka;
        this.timeoutMs = timeoutMs;
    }

    protected void send(String topic, String key, Object event) {
        try {
            kafka.send(topic, key, json.writeValueAsString(event)).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException(topic, e);
        } catch (Exception e) {
            throw new EventPublicationException(topic, e);
        }
    }
}
