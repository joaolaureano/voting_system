package com.voting.domain.ballot;

import static org.assertj.core.api.Assertions.assertThat;

import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.Vote;
import com.voting.domain.model.Votes;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import com.voting.domain.receipt.VoteReceipt;
import org.junit.jupiter.api.Test;

class VoteAdmissionTest {

    private final ReceiptPolicy policy = new Sha256ReceiptPolicy("pepper-de-teste");
    private static final ElectionSchedule AGENDA =
            ElectionSchedule.alwaysOpen(Votes.ELECTION);

    private final VoteAdmission admission = new VoteAdmission(AGENDA);

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
    void votoDepoisDoEncerramentoERejeitado() {
        VoteAdmission comPrazo = new VoteAdmission(new ElectionSchedule(
                Votes.ELECTION, Votes.T0.minusSeconds(3600), Votes.T0));
        Vote atrasado = Votes.of("voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", Votes.T0);

        AdmissionDecision decision = comPrazo.admit(atrasado, policy.issueFor(atrasado), null);

        assertThat(decision)
                .isEqualTo(new AdmissionDecision.Rejected(RejectionReason.ELECTION_CLOSED, null));
    }

    @Test
    void votoAntesDaAberturaERejeitadoComOutroMotivo() {
        VoteAdmission comPrazo = new VoteAdmission(new ElectionSchedule(
                Votes.ELECTION, Votes.T0.plusSeconds(60), Votes.T0.plusSeconds(3600)));
        Vote adiantado = Votes.of("voter-1", "cand-1");

        AdmissionDecision decision = comPrazo.admit(adiantado, policy.issueFor(adiantado), null);

        assertThat(decision)
                .isEqualTo(new AdmissionDecision.Rejected(RejectionReason.ELECTION_NOT_OPEN, null));
    }

    @Test
    void oUltimoInstanteAntesDoFechamentoAindaVale() {
        VoteAdmission comPrazo = new VoteAdmission(new ElectionSchedule(
                Votes.ELECTION, Votes.T0.minusSeconds(3600), Votes.T0));
        Vote noLimite = Votes.of("voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", Votes.T0.minusMillis(1));

        assertThat(comPrazo.admit(noLimite, policy.issueFor(noLimite), null).accepted()).isTrue();
    }

    // O prazo vem antes da duplicidade: para quem opera, "chegou fora do horario" e a
    // informacao acionavel, mesmo que o eleitor tambem ja tivesse votado.
    @Test
    void oPrazoEVerificadoAntesDaDuplicidade() {
        VoteAdmission comPrazo = new VoteAdmission(new ElectionSchedule(
                Votes.ELECTION, Votes.T0.minusSeconds(3600), Votes.T0));
        Vote atrasado = Votes.of("voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", Votes.T0);
        VoteReceipt anterior = policy.issueFor(Votes.of("voter-1", "cand-2"));

        AdmissionDecision decision = comPrazo.admit(atrasado, policy.issueFor(atrasado), anterior);

        assertThat(decision).isEqualTo(
                new AdmissionDecision.Rejected(RejectionReason.ELECTION_CLOSED, anterior));
    }

    @Test
    void votoDeOutraEleicaoERejeitado() {
        VoteAdmission outra = new VoteAdmission(
                ElectionSchedule.alwaysOpen(ElectionId.of("br-2030-presidencial")));
        Vote vote = Votes.of("voter-1", "cand-1");

        AdmissionDecision decision = outra.admit(vote, policy.issueFor(vote), null);

        assertThat(decision)
                .isEqualTo(new AdmissionDecision.Rejected(RejectionReason.WRONG_ELECTION, null));
    }
}
