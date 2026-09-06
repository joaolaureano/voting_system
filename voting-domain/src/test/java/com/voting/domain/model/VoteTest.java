package com.voting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoteTest {

    @Test
    void aceitaVotoNoPassado() {
        Vote vote = Vote.cast(
                Votes.ELECTION,
                VoterId.of("voter-1"),
                CandidateId.of("cand-1"),
                PartyId.of("PT-A"),
                Region.of("SP", "Santos"),
                Votes.T0.minusSeconds(30),
                Votes.T0);

        assertThat(vote.castAt()).isEqualTo(Votes.T0.minusSeconds(30));
    }

    @Test
    void toleraPequenaDessincronizacaoDeRelogio() {
        Instant quaseAgora = Votes.T0.plusSeconds(30);

        assertThat(Vote.cast(
                        Votes.ELECTION,
                        VoterId.of("voter-1"),
                        CandidateId.of("cand-1"),
                        PartyId.of("PT-A"),
                        Region.of("SP", "Santos"),
                        quaseAgora,
                        Votes.T0))
                .isNotNull();
    }

    @Test
    void recusaVotoNoFuturo() {
        assertThatThrownBy(() -> Vote.cast(
                        Votes.ELECTION,
                        VoterId.of("voter-1"),
                        CandidateId.of("cand-1"),
                        PartyId.of("PT-A"),
                        Region.of("SP", "Santos"),
                        Votes.T0.plusSeconds(3600),
                        Votes.T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futuro");
    }

    @Test
    void recusaIdentificadoresVazios() {
        assertThatThrownBy(() -> VoterId.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandidateId.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizaEstadoParaAgregacao() {
        assertThat(Region.of("sp", "Campinas").state()).isEqualTo("SP");
        assertThat(Region.of("sp", "Campinas")).isEqualTo(Region.of("SP", "Campinas"));
    }

    @Test
    void qualificaCidadePeloEstadoParaEvitarHomonimos() {
        assertThat(Region.of("mg", "Bom Jesus").cityKey()).isEqualTo("MG/Bom Jesus");
        assertThat(Region.of("rs", "Bom Jesus").cityKey()).isNotEqualTo(Region.of("mg", "Bom Jesus").cityKey());
    }
}
