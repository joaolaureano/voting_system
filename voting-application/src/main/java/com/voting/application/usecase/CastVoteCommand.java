package com.voting.application.usecase;

import java.time.Instant;

/**
 * Intencao de voto vinda da borda, ainda em tipos primitivos.
 *
 * <p>E o caso de uso que promove esses campos a value objects do dominio. Assim o adaptador
 * HTTP nao precisa conhecer o dominio, e uma mudanca de modelagem nao vaza para o controller.
 *
 * @param castAt momento declarado pelo cliente; {@code null} deixa o servidor carimbar
 */
public record CastVoteCommand(
        String electionId,
        String voterId,
        String candidateId,
        String partyId,
        String state,
        String city,
        Instant castAt) {
}
