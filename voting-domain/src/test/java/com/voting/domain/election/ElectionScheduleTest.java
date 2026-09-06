package com.voting.domain.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voting.domain.model.ElectionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ElectionScheduleTest {

    private static final ElectionId ELEICAO = ElectionId.of("br-2026-presidencial");
    private static final Instant ABRE = Instant.parse("2026-10-04T11:00:00Z");
    private static final Instant FECHA = Instant.parse("2026-10-04T20:00:00Z");

    private final ElectionSchedule agenda = new ElectionSchedule(ELEICAO, ABRE, FECHA);

    @Test
    void aceitaVotoDentroDoPeriodo() {
        assertThat(agenda.isOpenAt(Instant.parse("2026-10-04T15:30:00Z"))).isTrue();
        assertThat(agenda.isOpenAt(ABRE)).isTrue();
    }

    // Intervalo semiaberto: sem essa convencao, "encerra as 20:00" seria ambiguo para o voto
    // carimbado exatamente as 20:00:00.000.
    @Test
    void oInstanteDoFechamentoJaEstaForaDoPeriodo() {
        assertThat(agenda.isOpenAt(FECHA)).isFalse();
        assertThat(agenda.isOpenAt(FECHA.minusMillis(1))).isTrue();
        assertThat(agenda.hasClosedAt(FECHA)).isTrue();
    }

    @Test
    void recusaVotoAntesDaAbertura() {
        assertThat(agenda.isOpenAt(ABRE.minusMillis(1))).isFalse();
    }

    @Test
    void aEleicaoSemPrazoAceitaQualquerHorario() {
        ElectionSchedule semPrazo = ElectionSchedule.alwaysOpen(ELEICAO);

        assertThat(semPrazo.isOpenAt(Instant.EPOCH)).isTrue();
        assertThat(semPrazo.isOpenAt(Instant.parse("2200-01-01T00:00:00Z"))).isTrue();
        assertThat(semPrazo.isUnbounded()).isTrue();
        assertThat(agenda.isUnbounded()).isFalse();
    }

    @Test
    void recusaPeriodoInvertido() {
        assertThatThrownBy(() -> new ElectionSchedule(ELEICAO, FECHA, ABRE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecharia antes de abrir");
    }

    @Test
    void aExcecaoDizSeFaltaAbrirOuJaFechou() {
        assertThat(new ElectionClosedException(agenda, ABRE.minusSeconds(60)).getMessage())
                .contains("abre em");
        assertThat(new ElectionClosedException(agenda, FECHA.plusSeconds(60)).getMessage())
                .contains("encerrou em");
    }
}
