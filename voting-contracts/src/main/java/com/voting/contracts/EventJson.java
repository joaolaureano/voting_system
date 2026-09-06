package com.voting.contracts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configuracao unica de serializacao dos eventos.
 *
 * <p>Um so lugar define o formato do que trafega no Kafka, e API de ingestao e job Flink o
 * compartilham - e o que evita que produtor e consumidor divirjam silenciosamente.
 *
 * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} fica desligado de proposito: um produtor mais novo
 * pode acrescentar campos sem derrubar consumidores antigos.
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = create();

    private EventJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Nova instancia configurada, para quem precisa registrar modulos adicionais. */
    public static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
