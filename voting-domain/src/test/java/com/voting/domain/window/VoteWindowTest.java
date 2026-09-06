package com.voting.domain.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoteWindowTest {

    private static final Duration UM_MINUTO = Duration.ofMinutes(1);

    @Test
    void alinhaAJanelaAEpoca() {
        VoteWindow janela = VoteWindow.containing(Instant.parse("2026-10-04T13:05:37.412Z"), UM_MINUTO);

        assertThat(janela.start()).isEqualTo(Instant.parse("2026-10-04T13:05:00Z"));
        assertThat(janela.end()).isEqualTo(Instant.parse("2026-10-04T13:06:00Z"));
        assertThat(janela.id()).isEqualTo("2026-10-04T13:05:00Z");
    }

    @Test
    void votosDoMesmoMinutoCaemNaMesmaJanela() {
        assertThat(VoteWindow.containing(Instant.parse("2026-10-04T13:05:00Z"), UM_MINUTO))
                .isEqualTo(VoteWindow.containing(Instant.parse("2026-10-04T13:05:59.999Z"), UM_MINUTO));
    }

    @Test
    void oInstanteFinalJaPertenceAProximaJanela() {
        assertThat(VoteWindow.containing(Instant.parse("2026-10-04T13:06:00Z"), UM_MINUTO).id())
                .isEqualTo("2026-10-04T13:06:00Z");
    }

    @Test
    void aJanelaEDeterministicaEIndependenteDoMomentoDoCalculo() {
        Instant voto = Instant.parse("2026-10-04T13:05:37.412Z");

        assertThat(VoteWindow.containing(voto, UM_MINUTO))
                .isEqualTo(VoteWindow.containing(voto, UM_MINUTO));
    }

    @Test
    void funcionaAntesDaEpoca() {
        VoteWindow janela = VoteWindow.containing(Instant.parse("1969-12-31T23:59:30Z"), UM_MINUTO);

        assertThat(janela.start()).isEqualTo(Instant.parse("1969-12-31T23:59:00Z"));
    }

    @Test
    void recusaTamanhoInvalido() {
        assertThatThrownBy(() -> VoteWindow.containing(Instant.EPOCH, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
