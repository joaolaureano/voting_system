package com.voting.contracts;

import java.time.Instant;

/**
 * Voto recusado pela apuracao, publicado em {@code votes.rejected}.
 *
 * <p>Nada e descartado em silencio: uma duplicata vira um evento auditavel, com o recibo do
 * voto que de fato vale ({@code originalReceipt}) para que o eleitor possa ser orientado.
 */
public record RejectedVoteEvent(
        int schemaVersion,
        String electionId,
        String voterId,
        String candidateId,
        String receipt,
        String reason,
        String originalReceipt,
        Instant rejectedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
