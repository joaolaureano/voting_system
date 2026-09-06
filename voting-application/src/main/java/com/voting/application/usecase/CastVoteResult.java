package com.voting.application.usecase;

import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;

/**
 * Resultado do registro do voto.
 *
 * <p>"Registrado" aqui significa <em>aceito para apuracao</em>, e nao "computado": a decisao
 * de unicidade acontece adiante, no Flink, que tem o estado de todos os eleitores. O recibo
 * e o que permite ao eleitor conferir depois qual foi o desfecho.
 */
public record CastVoteResult(VoteReceipt receipt, Vote vote) {

    public String receiptHash() {
        return receipt.hash();
    }
}
