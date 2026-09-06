package com.voting.contracts;

import java.time.Instant;

/**
 * Contagem corrente de uma chave em uma dimensao, publicada em {@code results.by-*}.
 *
 * <p>Chave Kafka: {@code key}. Com os topicos compactados, a ultima contagem de cada chave
 * sobrevive indefinidamente - o consumidor que ler do inicio reconstroi o placar completo.
 */
public record TallyUpdateEvent(
        int schemaVersion,
        String dimension,
        String key,
        long count,
        Instant updatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
