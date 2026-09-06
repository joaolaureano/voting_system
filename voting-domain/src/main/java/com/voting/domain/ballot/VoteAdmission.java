package com.voting.domain.ballot;

import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;
import java.util.Objects;

/**
 * As regras de admissao de um voto na apuracao, expressas como funcao pura.
 *
 * <p>Duas regras hoje: o voto tem de pertencer a esta eleicao e ao seu periodo, e o eleitor
 * so vota uma vez. O dominio decide; quem guarda o voto anterior de cada eleitor e o
 * adaptador - no caso, o estado por chave do Flink. Assim as regras sao testaveis sem subir
 * um cluster, e trocar o mecanismo de estado nao mexe nelas.
 *
 * <p>A ordem das verificacoes importa para o diagnostico: um voto fora do prazo e recusado
 * como tal mesmo que o eleitor ja tivesse votado, porque a informacao acionavel ali e o
 * prazo, nao a duplicidade.
 */
public final class VoteAdmission {

    private final ElectionSchedule schedule;

    public VoteAdmission(ElectionSchedule schedule) {
        this.schedule = Objects.requireNonNull(schedule, "schedule");
    }

    /**
     * @param vote            voto que chegou
     * @param receipt         recibo emitido para esse voto
     * @param previousReceipt recibo do voto ja computado deste eleitor, ou {@code null}
     */
    public AdmissionDecision admit(Vote vote, VoteReceipt receipt, VoteReceipt previousReceipt) {
        Objects.requireNonNull(vote, "vote");
        Objects.requireNonNull(receipt, "receipt");

        if (!schedule.election().equals(vote.electionId())) {
            return new AdmissionDecision.Rejected(RejectionReason.WRONG_ELECTION, previousReceipt);
        }
        if (!schedule.isOpenAt(vote.castAt())) {
            RejectionReason motivo = vote.castAt().isBefore(schedule.opensAt())
                    ? RejectionReason.ELECTION_NOT_OPEN
                    : RejectionReason.ELECTION_CLOSED;
            return new AdmissionDecision.Rejected(motivo, previousReceipt);
        }
        if (previousReceipt != null) {
            return new AdmissionDecision.Rejected(RejectionReason.DUPLICATE_VOTE, previousReceipt);
        }
        return new AdmissionDecision.Accepted(receipt);
    }
}
