package com.voting.streaming.merkle;

import com.voting.contracts.ControlEvent;
import com.voting.contracts.VoteCastEvent;
import java.time.Instant;

/**
 * Envelope interno do job: um voto ou um evento de controle, ambos com um instante.
 *
 * <p>Nao e contrato de fio - existe para que os dois topicos possam ser unidos num fluxo so,
 * com uma marca d'agua unica. E a uniao que resolve o travamento: enquanto os batimentos
 * chegarem, o tempo de evento avanca mesmo sem votos, e as janelas fecham.
 *
 * <p>Os votos seguem adiante; os eventos de controle morrem no filtro logo depois. Eles
 * cumpriram seu papel so por terem existido no fluxo com um horario.
 */
public record TimelineEvent(VoteCastEvent vote, ControlEvent control, Instant at) {

    public static TimelineEvent ofVote(VoteCastEvent vote) {
        return new TimelineEvent(vote, null, vote.castAt());
    }

    public static TimelineEvent ofControl(ControlEvent control) {
        return new TimelineEvent(null, control, control.at());
    }

    /**
     * Nome sem o prefixo {@code is}, de proposito: o Jackson leria {@code isVote()} como o
     * getter do componente {@code vote} e serializaria {@code "vote": true}, destruindo o
     * objeto no caminho de volta. O envelope trafega como JSON entre os operadores.
     */
    public boolean hasVote() {
        return vote != null;
    }

    /** Idem: {@code getTimestamp}/{@code timestamp} viraria um campo a mais no JSON. */
    public long eventTimeMillis() {
        return at.toEpochMilli();
    }
}
