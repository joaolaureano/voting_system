package com.voting.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.domain.ballot.AdmissionDecision;
import com.voting.domain.ballot.RejectionReason;
import com.voting.domain.model.CandidateId;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.PartyId;
import com.voting.domain.model.Region;
import com.voting.domain.model.Vote;
import com.voting.domain.model.VoterId;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import com.voting.domain.receipt.VoteReceipt;
import com.voting.domain.tally.TallyDimension;
import com.voting.domain.tally.VoteTally;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoteEventMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-04T13:00:00Z");
    private final ObjectMapper json = EventJson.mapper();

    private final Vote vote = new Vote(
            ElectionId.of("br-2026-presidencial"),
            VoterId.of("voter-1"),
            CandidateId.of("cand-1"),
            PartyId.of("PT-A"),
            Region.of("sp", "Sao Paulo"),
            T0);
    private final VoteReceipt receipt = new Sha256ReceiptPolicy("pepper-de-teste").issueFor(vote);

    @Test
    void votoSobreviveAoRoundTripDominioEventoDominio() throws Exception {
        VoteCastEvent event = VoteEventMapper.toEvent(vote, receipt);
        String wire = json.writeValueAsString(event);

        Vote reconstruido = VoteEventMapper.toDomain(json.readValue(wire, VoteCastEvent.class));

        assertThat(reconstruido).isEqualTo(vote);
        assertThat(VoteEventMapper.receiptOf(json.readValue(wire, VoteCastEvent.class))).isEqualTo(receipt);
    }

    @Test
    void instantesUsamIso8601NoFio() throws Exception {
        String wire = json.writeValueAsString(VoteEventMapper.toEvent(vote, receipt));

        assertThat(wire).contains("\"castAt\":\"2026-10-04T13:00:00Z\"");
    }

    @Test
    void consumidorIgnoraCamposDesconhecidosDeProdutorMaisNovo() throws Exception {
        String wire = "{\"schemaVersion\":1,\"electionId\":\"br-2026-presidencial\",\"voterId\":\"voter-1\","
                + "\"candidateId\":\"cand-1\",\"partyId\":\"PT-A\",\"state\":\"SP\",\"city\":\"Sao Paulo\","
                + "\"castAt\":\"2026-10-04T13:00:00Z\",\"receipt\":\"" + receipt.hash() + "\","
                + "\"campoDoFuturo\":42}";

        assertThat(VoteEventMapper.toDomain(json.readValue(wire, VoteCastEvent.class))).isEqualTo(vote);
    }

    @Test
    void rejeicaoCarregaMotivoEReciboOriginal() throws Exception {
        VoteCastEvent event = VoteEventMapper.toEvent(vote, receipt);
        AdmissionDecision.Rejected rejection =
                new AdmissionDecision.Rejected(RejectionReason.DUPLICATE_VOTE, receipt);

        RejectedVoteEvent rejected = VoteEventMapper.toRejectedEvent(event, rejection, T0);

        assertThat(rejected.reason()).isEqualTo("DUPLICATE_VOTE");
        assertThat(rejected.originalReceipt()).isEqualTo(receipt.hash());
        assertThat(json.readValue(json.writeValueAsString(rejected), RejectedVoteEvent.class))
                .isEqualTo(rejected);
    }

    @Test
    void comprovanteNaoRevelaOCandidato() throws Exception {
        String wire = json.writeValueAsString(VoteEventMapper.toReceiptEvent(vote, receipt, T0));

        assertThat(wire).doesNotContain("cand-1").contains(receipt.hash());
    }

    @Test
    void apuracaoViraEventoComDimensaoEContagem() {
        TallyUpdateEvent event = VoteEventMapper.toTallyEvent(
                new VoteTally(TallyDimension.STATE, "SP", 7L, T0));

        assertThat(event.dimension()).isEqualTo("STATE");
        assertThat(event.key()).isEqualTo("SP");
        assertThat(event.count()).isEqualTo(7L);
    }
}
