package com.voting.domain.tally;

import java.time.Instant;
import java.util.Objects;

/**
 * Contagem corrente de votos de uma chave dentro de uma dimensao.
 *
 * <p>Imutavel: {@link #increment(Instant)} devolve uma nova apuracao. O acumulador vive no
 * estado do Flink; o dominio so descreve a transicao.
 */
public record VoteTally(TallyDimension dimension, String key, long count, Instant updatedAt) {

    public VoteTally {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (key == null || key.isBlank()) {
            throw new InvalidTallyException("chave de apuracao nao pode ser vazia");
        }
        if (count < 0) {
            throw new InvalidTallyException("contagem nao pode ser negativa: " + count);
        }
    }

    public VoteTally increment(Instant at) {
        return new VoteTally(dimension, key, count + 1, at);
    }
}
