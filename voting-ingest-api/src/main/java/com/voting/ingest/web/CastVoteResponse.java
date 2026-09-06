package com.voting.ingest.web;

import com.voting.application.usecase.CastVoteResult;
import java.time.Instant;

/**
 * Resposta ao eleitor.
 *
 * <p>{@code status} e {@code ACCEPTED}, nunca "computado": neste ponto o voto foi aceito para
 * apuracao, e a unicidade sera decidida adiante pelo Flink. O recibo e o que permite conferir
 * o desfecho depois.
 */
public record CastVoteResponse(String receipt, String status, Instant castAt) {

    public static CastVoteResponse of(CastVoteResult result) {
        return new CastVoteResponse(result.receiptHash(), "ACCEPTED", result.vote().castAt());
    }
}
