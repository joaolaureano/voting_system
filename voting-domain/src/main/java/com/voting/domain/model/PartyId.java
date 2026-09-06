package com.voting.domain.model;

/** Partido do candidato, replicado no voto para permitir agregacao sem join. */
public record PartyId(String value) {

    public PartyId {
        value = Identifiers.required(value, "partyId");
    }

    public static PartyId of(String value) {
        return new PartyId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
