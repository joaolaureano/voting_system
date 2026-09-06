package com.voting.domain.model;

/** Identifica a eleicao a que um voto pertence. */
public record ElectionId(String value) {

    public ElectionId {
        value = Identifiers.required(value, "electionId");
    }

    public static ElectionId of(String value) {
        return new ElectionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
