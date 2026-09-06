package com.voting.domain.election;

import com.voting.domain.model.ElectionId;
import java.time.Instant;
import java.util.Objects;

/**
 * O periodo em que uma eleicao aceita votos.
 *
 * <p>Intervalo semiaberto {@code [opensAt, closesAt)}: o instante de fechamento ja esta
 * fora. Sem essa convencao, "encerra as 18:00" seria ambiguo para o voto carimbado
 * exatamente as 18:00:00.000.
 *
 * <p>A decisao usa o {@code castAt} do voto - o horario que o servidor carimbou na chegada -
 * e nunca o horario de processamento. Um voto que a rede atrasou continua valendo pelo
 * momento em que entrou no sistema, e reprocessar o log nao muda quem entrou e quem ficou de
 * fora.
 */
public record ElectionSchedule(ElectionId election, Instant opensAt, Instant closesAt) {

    public ElectionSchedule {
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(opensAt, "opensAt");
        Objects.requireNonNull(closesAt, "closesAt");
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException(
                    "a eleicao fecharia antes de abrir: " + opensAt + " .. " + closesAt);
        }
    }

    /**
     * Eleicao sem prazo, para desenvolvimento e testes.
     *
     * <p>Existe para que o sistema continue subindo sem configuracao de horario. Em producao,
     * uma eleicao sem fechamento e um erro de operacao, nao um caso de uso.
     */
    public static ElectionSchedule alwaysOpen(ElectionId election) {
        return new ElectionSchedule(election, Instant.MIN, Instant.MAX);
    }

    public boolean isOpenAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return !at.isBefore(opensAt) && at.isBefore(closesAt);
    }

    public boolean hasClosedAt(Instant at) {
        return !at.isBefore(closesAt);
    }

    /** True quando nao ha prazo definido. */
    public boolean isUnbounded() {
        return opensAt.equals(Instant.MIN) && closesAt.equals(Instant.MAX);
    }
}
