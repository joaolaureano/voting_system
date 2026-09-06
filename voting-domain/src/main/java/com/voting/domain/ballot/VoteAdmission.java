package com.voting.domain.ballot;

import com.voting.domain.model.ElectionId;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;
import java.util.Objects;

/**
 * A regra "um voto por eleitor", expressa como funcao pura.
 *
 * <p>O dominio decide; quem guarda o voto anterior de cada eleitor e o adaptador - no caso,
 * o estado por chave do Flink. Assim a regra e testavel sem subir um cluster, e trocar o
 * mecanismo de estado (Flink, Redis, banco) nao mexe na regra.
 */
public final class VoteAdmission {

    private final ElectionId election;

    public VoteAdmission(ElectionId election) {
        this.election = Objects.requireNonNull(election, "election");
    }

    /**
     * @param vote            voto que chegou
     * @param receipt         recibo emitido para esse voto
     * @param previousReceipt recibo do voto ja computado deste eleitor, ou {@code null}
     */
    public AdmissionDecision admit(Vote vote, VoteReceipt receipt, VoteReceipt previousReceipt) {
        Objects.requireNonNull(vote, "vote");
        Objects.requireNonNull(receipt, "receipt");
        if (!election.equals(vote.electionId())) {
            return new AdmissionDecision.Rejected(RejectionReason.WRONG_ELECTION, previousReceipt);
        }
        if (previousReceipt != null) {
            return new AdmissionDecision.Rejected(RejectionReason.DUPLICATE_VOTE, previousReceipt);
        }
        return new AdmissionDecision.Accepted(receipt);
    }
}
