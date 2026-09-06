package com.voting.domain.model;

/**
 * Identidade do eleitor. E a chave de particionamento no Kafka e a chave de estado
 * do dedup no Flink: todos os votos de um mesmo eleitor precisam cair na mesma particao.
 */
public record VoterId(String value) {

    public VoterId {
        value = Identifiers.required(value, "voterId");
    }

    public static VoterId of(String value) {
        return new VoterId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
