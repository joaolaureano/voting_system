package com.voting.contracts;

import java.time.Instant;

/**
 * Fechamento de uma janela de apuracao, publicado em {@code votes.windows}.
 *
 * <p>O Flink emite este marcador quando a marca d'agua passa o fim da janela - ou seja,
 * quando ele garante que nao chega mais nenhum voto daquele intervalo. O {@code count} diz
 * quantos votos a janela teve.
 *
 * <p>E isso que permite ao construtor da arvore saber que tem o conjunto completo, em vez de
 * adivinhar por timeout: ele sela a janela quando junta exatamente {@code count} folhas, e
 * sabe que ha uma lacuna quando nao junta.
 */
public record WindowMarkerEvent(
        int schemaVersion,
        String windowId,
        Instant windowStart,
        Instant windowEnd,
        long count) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
