package com.voting.domain.ballot;

import static org.assertj.core.api.Assertions.assertThat;

import com.voting.domain.model.ElectionId;
import com.voting.domain.model.Vote;
import com.voting.domain.model.Votes;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import com.voting.domain.receipt.VoteReceipt;
import org.junit.jupiter.api.Test;

class VoteAdmissionTest {

    private final ReceiptPolicy policy = new Sha256ReceiptPolicy("pepper-de-teste");
    private final VoteAdmission admission = new VoteAdmission(Votes.ELECTION);

    @Test
    void primeiroVotoDoEleitorEAdmitido() {
        Vote vote = Votes.of("voter-1", "cand-1");
        VoteReceipt receipt = policy.issueFor(vote);

        AdmissionDecision decision = admission.admit(vote, receipt, null);

        assertThat(decision).isEqualTo(new AdmissionDecision.Accepted(receipt));
        assertThat(decision.accepted()).isTrue();
    }

    @Test
    void segundoVotoDoMesmoEleitorERejeitadoEDevolveOReciboOriginal() {
        Vote primeiro = Votes.of("voter-1", "cand-1");
        VoteReceipt original = policy.issueFor(primeiro);
        Vote segundo = Votes.of("voter-1", "cand-2");

        AdmissionDecision decision = admission.admit(segundo, policy.issueFor(segundo), original);

        assertThat(decision)
                .isEqualTo(new AdmissionDecision.Rejected(RejectionReason.DUPLICATE_VOTE, original));
        assertThat(decision.accepted()).isFalse();
    }

    @Test
    void votoDeOutraEleicaoERejeitado() {
        VoteAdmission outra = new VoteAdmission(ElectionId.of("br-2030-presidencial"));
        Vote vote = Votes.of("voter-1", "cand-1");

        AdmissionDecision decision = outra.admit(vote, policy.issueFor(vote), null);

        assertThat(decision)
                .isEqualTo(new AdmissionDecision.Rejected(RejectionReason.WRONG_ELECTION, null));
    }
}
