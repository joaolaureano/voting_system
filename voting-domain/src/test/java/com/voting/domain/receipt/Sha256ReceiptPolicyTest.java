package com.voting.domain.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voting.domain.model.Vote;
import com.voting.domain.model.Votes;
import org.junit.jupiter.api.Test;

class Sha256ReceiptPolicyTest {

    private final ReceiptPolicy policy = new Sha256ReceiptPolicy("pepper-de-teste");

    @Test
    void mesmoVotoProduzMesmoRecibo() {
        Vote vote = Votes.of("voter-1", "cand-1");

        assertThat(policy.issueFor(vote)).isEqualTo(policy.issueFor(vote));
    }

    @Test
    void reciboTemFormatoSha256Hex() {
        assertThat(policy.issueFor(Votes.of("voter-1", "cand-1")).hash())
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void votosDiferentesProduzemRecibosDiferentes() {
        assertThat(policy.issueFor(Votes.of("voter-1", "cand-1")))
                .isNotEqualTo(policy.issueFor(Votes.of("voter-2", "cand-1")))
                .isNotEqualTo(policy.issueFor(Votes.of("voter-1", "cand-2")));
    }

    @Test
    void pepperDiferenteProduzReciboDiferente() {
        Vote vote = Votes.of("voter-1", "cand-1");

        assertThat(new Sha256ReceiptPolicy("outro-pepper").issueFor(vote))
                .isNotEqualTo(policy.issueFor(vote));
    }

    @Test
    void recusaReciboComFormatoInvalido() {
        assertThatThrownBy(() -> VoteReceipt.of("nao-e-um-hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoteReceipt.of("A".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
