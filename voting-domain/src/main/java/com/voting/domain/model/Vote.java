package com.voting.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Agregado raiz: um voto ja emitido por um eleitor.
 *
 * <p>Nesta fase o cedula e simples - exatamente um candidato por eleitor - e o partido
 * viaja junto com o voto para que a agregacao por partido nao precise de join com um
 * cadastro externo.
 *
 * <p>O agregado e imutavel: nao existe "alterar voto". Um segundo voto do mesmo eleitor e
 * um evento novo, que sera rejeitado por {@link com.voting.domain.ballot.VoteAdmission}.
 */
public record Vote(
        ElectionId electionId,
        VoterId voterId,
        CandidateId candidateId,
        PartyId partyId,
        Region region,
        Instant castAt) {

    /** Folga aceita para relogios dessincronizados entre cliente e servidor. */
    public static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(1);

    public Vote {
        Objects.requireNonNull(electionId, "electionId");
        Objects.requireNonNull(voterId, "voterId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(castAt, "castAt");
    }

    /**
     * Fabrica que aplica a invariante temporal. O "agora" e um parametro, e nao
     * {@code Instant.now()}, para que o dominio permaneca deterministico e testavel - quem
     * conhece o relogio e a borda da aplicacao.
     */
    public static Vote cast(
            ElectionId electionId,
            VoterId voterId,
            CandidateId candidateId,
            PartyId partyId,
            Region region,
            Instant castAt,
            Instant now) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(castAt, "castAt");
        if (castAt.isAfter(now.plus(CLOCK_SKEW_TOLERANCE))) {
            throw new IllegalArgumentException("castAt esta no futuro: " + castAt + " > " + now);
        }
        return new Vote(electionId, voterId, candidateId, partyId, region, castAt);
    }
}
