package com.voting.application.usecase;

import com.voting.application.port.ReceiptPublisher;
import com.voting.application.port.VoteEventPublisher;
import com.voting.domain.model.CandidateId;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.PartyId;
import com.voting.domain.model.Region;
import com.voting.domain.model.Vote;
import com.voting.domain.model.VoterId;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.VoteReceipt;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Registrar um voto: validar, emitir o comprovante e entregar as duas portas.
 *
 * <p>A ordem importa. O voto vai primeiro para a apuracao e so depois o comprovante e
 * registrado: se a segunda publicacao falhar, existe um voto sem comprovante consultavel -
 * incomodo, mas recuperavel. A ordem inversa produziria um comprovante para um voto que
 * nunca entrou na apuracao, que e mentir para o eleitor.
 */
public final class CastVoteUseCase {

    private final ElectionId election;
    private final ReceiptPolicy receiptPolicy;
    private final VoteEventPublisher votePublisher;
    private final ReceiptPublisher receiptPublisher;
    private final Clock clock;

    public CastVoteUseCase(
            ElectionId election,
            ReceiptPolicy receiptPolicy,
            VoteEventPublisher votePublisher,
            ReceiptPublisher receiptPublisher,
            Clock clock) {
        this.election = Objects.requireNonNull(election, "election");
        this.receiptPolicy = Objects.requireNonNull(receiptPolicy, "receiptPolicy");
        this.votePublisher = Objects.requireNonNull(votePublisher, "votePublisher");
        this.receiptPublisher = Objects.requireNonNull(receiptPublisher, "receiptPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CastVoteResult execute(CastVoteCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();

        ElectionId commandElection =
                command.electionId() == null ? election : ElectionId.of(command.electionId());
        if (!election.equals(commandElection)) {
            throw new IllegalArgumentException(
                    "voto endereçado a outra eleicao: " + commandElection + " != " + election);
        }

        Vote vote = Vote.cast(
                election,
                VoterId.of(command.voterId()),
                CandidateId.of(command.candidateId()),
                PartyId.of(command.partyId()),
                Region.of(command.state(), command.city()),
                command.castAt() == null ? now : command.castAt(),
                now);

        VoteReceipt receipt = receiptPolicy.issueFor(vote);
        votePublisher.publish(vote, receipt);
        receiptPublisher.publish(vote, receipt);

        return new CastVoteResult(receipt, vote);
    }
}
