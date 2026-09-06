package com.voting.domain.election;

import com.voting.domain.DomainException;
import java.time.Instant;

/**
 * O voto chegou fora do periodo da eleicao.
 *
 * <p>Usada no caminho sincrono da borda, para que o eleitor receba a recusa na hora em vez de
 * um comprovante que a apuracao vai descartar depois. A autoridade sobre o prazo continua
 * sendo o Flink - a borda so antecipa a resposta.
 */
public class ElectionClosedException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient ElectionSchedule schedule;
    private final transient Instant attemptedAt;

    public ElectionClosedException(ElectionSchedule schedule, Instant attemptedAt) {
        super("VOTACAO_FECHADA", mensagem(schedule, attemptedAt));
        this.schedule = schedule;
        this.attemptedAt = attemptedAt;
    }

    private static String mensagem(ElectionSchedule schedule, Instant attemptedAt) {
        if (attemptedAt.isBefore(schedule.opensAt())) {
            return "a votacao abre em " + schedule.opensAt();
        }
        return "a votacao encerrou em " + schedule.closesAt();
    }

    public ElectionSchedule schedule() {
        return schedule;
    }

    public Instant attemptedAt() {
        return attemptedAt;
    }
}
