package com.voting.domain.window;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Fatia de tempo sobre a qual a apuracao e selada.
 *
 * <p>E a unidade de commitment da Merkle Tree: todos os votos cujo {@code castAt} cai na mesma
 * janela entram na mesma arvore, e a raiz dessa arvore compromete exatamente esse conjunto.
 *
 * <p>A janela e derivada do <em>horario do voto</em>, e nao do horario de chegada. Duas
 * reprocessagens do mesmo log produzem as mesmas janelas com os mesmos votos, o que e a
 * condicao para que um auditor consiga recalcular a raiz. Janelas por horario de chegada
 * dariam arvores diferentes a cada replay.
 *
 * <p>O alinhamento e a epoca, como nas janelas em cascata do Flink: uma janela de 60s comeca
 * sempre num minuto cheio. Isso mantem os dois lados de acordo sem precisarem se falar.
 */
public record VoteWindow(Instant start, Instant end) {

    public VoteWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("janela vazia: " + start + " .. " + end);
        }
    }

    /** Janela alinhada a epoca que contem {@code instante}. */
    public static VoteWindow containing(Instant instante, Duration tamanho) {
        Objects.requireNonNull(instante, "instante");
        long tamanhoMs = tamanho.toMillis();
        if (tamanhoMs <= 0) {
            throw new IllegalArgumentException("tamanho da janela deve ser positivo");
        }
        long inicio = Math.floorDiv(instante.toEpochMilli(), tamanhoMs) * tamanhoMs;
        return new VoteWindow(Instant.ofEpochMilli(inicio), Instant.ofEpochMilli(inicio + tamanhoMs));
    }

    /**
     * Identificador da janela: o instante inicial em ISO-8601.
     *
     * <p>Legivel e ordenavel lexicograficamente, entao serve como chave no Kafka e como
     * numero de sequencia da cadeia de raizes sem precisar de um contador a parte.
     */
    public String id() {
        return start.toString();
    }
}
