package com.voting.contracts;

import java.time.Instant;

/**
 * Voto aceito pela borda e publicado em {@code votes.cast}.
 *
 * <p>Chave Kafka: {@code voterId}. Isso coloca todos os votos de um mesmo eleitor na mesma
 * particao, condicao para que o dedup por chave no Flink veja a duplicata.
 *
 * <p>Campos primitivos, e nao os value objects do dominio: o contrato de fio precisa poder
 * evoluir sem arrastar a modelagem junto.
 */
public record VoteCastEvent(
        int schemaVersion,
        String electionId,
        String voterId,
        String candidateId,
        String partyId,
        String state,
        String city,
        Instant castAt,
        String receipt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
