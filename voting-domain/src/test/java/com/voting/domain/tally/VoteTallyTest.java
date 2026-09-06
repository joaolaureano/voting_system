package com.voting.domain.tally;

import static org.assertj.core.api.Assertions.assertThat;

import com.voting.domain.model.Vote;
import com.voting.domain.model.Votes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VoteTallyTest {

    @Test
    void incrementoNaoMutaAApuracaoAnterior() {
        VoteTally zero = new VoteTally(TallyDimension.CANDIDATE, "cand-1", 0L, Votes.T0);
        VoteTally um = zero.increment(Votes.T0.plusSeconds(1));

        assertThat(zero.count()).isZero();
        assertThat(um.count()).isEqualTo(1L);
        assertThat(um.updatedAt()).isEqualTo(Votes.T0.plusSeconds(1));
    }

    @ParameterizedTest
    @CsvSource({
        "CANDIDATE, cand-1",
        "STATE, SP",
        "CITY, SP/Sao Paulo",
        "PARTY, PT-A"
    })
    void cadaDimensaoExtraiSuaChaveDoVoto(TallyDimension dimension, String chaveEsperada) {
        Vote vote = Votes.of("voter-1", "cand-1");

        assertThat(dimension.keyOf(vote)).isEqualTo(chaveEsperada);
    }

    @Test
    void cadaDimensaoTemSeuTopicoDeApuracao() {
        assertThat(TallyDimension.values())
                .extracting(TallyDimension::topic)
                .containsExactlyInAnyOrder(
                        "results.by-candidate", "results.by-state", "results.by-city", "results.by-party");
    }
}
