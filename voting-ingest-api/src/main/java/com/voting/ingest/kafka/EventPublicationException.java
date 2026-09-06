package com.voting.ingest.kafka;

/**
 * O evento nao chegou ao Kafka. Vira 503 na borda: o voto e valido, o registro e que falhou.
 */
public class EventPublicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventPublicationException(String topic, Throwable cause) {
        super("falha ao publicar em " + topic, cause);
    }
}
