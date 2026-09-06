package com.voting.domain.model;

import java.time.Instant;

/** Construtor de votos para os testes, com valores plausiveis por padrao. */
public final class Votes {

    public static final Instant T0 = Instant.parse("2026-10-04T13:00:00Z");
    public static final ElectionId ELECTION = ElectionId.of("br-2026-presidencial");

    private Votes() {
    }

    public static Vote of(String voterId, String candidateId) {
        return of(voterId, candidateId, "PT-A", "sp", "Sao Paulo", T0);
    }

    public static Vote of(String voterId, String candidateId, String party, String state, String city, Instant at) {
        return new Vote(
                ELECTION,
                VoterId.of(voterId),
                CandidateId.of(candidateId),
                PartyId.of(party),
                Region.of(state, city),
                at);
    }
}
