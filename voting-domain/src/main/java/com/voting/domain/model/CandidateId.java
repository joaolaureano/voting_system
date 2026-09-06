package com.voting.domain.model;

/** Identidade do candidato escolhido. */
public record CandidateId(String value) {

    public CandidateId {
        value = Identifiers.required(value, "candidateId");
    }

    public static CandidateId of(String value) {
        return new CandidateId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
