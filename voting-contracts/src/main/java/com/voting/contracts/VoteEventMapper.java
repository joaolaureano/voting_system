package com.voting.contracts;

import com.voting.domain.ballot.AdmissionDecision;
import com.voting.domain.model.CandidateId;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.PartyId;
import com.voting.domain.model.Region;
import com.voting.domain.model.Vote;
import com.voting.domain.model.VoterId;
import com.voting.domain.receipt.VoteReceipt;
import com.voting.domain.tally.VoteTally;
import com.voting.domain.window.VoteWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Traducao entre o dominio e os contratos de fio.
 *
 * <p>Esta classe e a fronteira: o dominio nunca ve um evento, e nenhum adaptador constroi um
 * {@link Vote} na mao. Concentrar a traducao aqui e o que mantem os dois lados livres para
 * evoluir em ritmos diferentes.
 */
public final class VoteEventMapper {

    private VoteEventMapper() {
    }

    public static VoteCastEvent toEvent(Vote vote, VoteReceipt receipt) {
        Objects.requireNonNull(vote, "vote");
        Objects.requireNonNull(receipt, "receipt");
        return new VoteCastEvent(
                VoteCastEvent.CURRENT_SCHEMA_VERSION,
                vote.electionId().value(),
                vote.voterId().value(),
                vote.candidateId().value(),
                vote.partyId().value(),
                vote.region().state(),
                vote.region().city(),
                vote.castAt(),
                receipt.hash());
    }

    public static Vote toDomain(VoteCastEvent event) {
        Objects.requireNonNull(event, "event");
        return new Vote(
                ElectionId.of(event.electionId()),
                VoterId.of(event.voterId()),
                CandidateId.of(event.candidateId()),
                PartyId.of(event.partyId()),
                Region.of(event.state(), event.city()),
                event.castAt());
    }

    public static VoteReceipt receiptOf(VoteCastEvent event) {
        return VoteReceipt.of(event.receipt());
    }

    public static ReceiptEvent toReceiptEvent(Vote vote, VoteReceipt receipt, Instant issuedAt) {
        return new ReceiptEvent(
                ReceiptEvent.CURRENT_SCHEMA_VERSION,
                receipt.hash(),
                vote.electionId().value(),
                vote.voterId().value(),
                issuedAt);
    }

    public static RejectedVoteEvent toRejectedEvent(
            VoteCastEvent event, AdmissionDecision.Rejected rejection, Instant rejectedAt) {
        Objects.requireNonNull(rejection, "rejection");
        VoteReceipt original = rejection.originalReceipt();
        return new RejectedVoteEvent(
                RejectedVoteEvent.CURRENT_SCHEMA_VERSION,
                event.electionId(),
                event.voterId(),
                event.candidateId(),
                event.receipt(),
                rejection.reason().name(),
                original == null ? null : original.hash(),
                rejectedAt);
    }

    /**
     * Converte um voto admitido no evento que alimenta a Merkle Tree.
     *
     * <p>A janela sai do {@code castAt}, e nao do relogio: e o que faz duas reprocessagens do
     * mesmo log cairem nas mesmas janelas.
     */
    public static AcceptedVoteEvent toAcceptedEvent(VoteCastEvent event, Duration tamanhoDaJanela) {
        Objects.requireNonNull(event, "event");
        return new AcceptedVoteEvent(
                AcceptedVoteEvent.CURRENT_SCHEMA_VERSION,
                event.receipt(),
                event.voterId(),
                VoteWindow.containing(event.castAt(), tamanhoDaJanela).id(),
                event.castAt());
    }

    public static TallyUpdateEvent toTallyEvent(VoteTally tally) {
        Objects.requireNonNull(tally, "tally");
        return new TallyUpdateEvent(
                TallyUpdateEvent.CURRENT_SCHEMA_VERSION,
                tally.dimension().name(),
                tally.key(),
                tally.count(),
                tally.updatedAt());
    }
}
