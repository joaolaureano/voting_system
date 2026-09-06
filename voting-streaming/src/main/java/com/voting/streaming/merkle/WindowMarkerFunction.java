package com.voting.streaming.merkle;

import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.WindowMarkerEvent;
import java.time.Instant;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Emite o marcador de fechamento de uma janela, com quantos votos ela teve.
 *
 * <p>Roda sobre a janela em cascata por horario de evento: o Flink so a dispara quando a marca
 * d'agua passa o fim do intervalo, ou seja, quando garante que nao chega mais nada daquela
 * janela. Essa garantia e o produto aqui - a contagem sozinha ja existe em
 * {@code results.by-*}; o que o construtor da arvore precisa e do sinal de "completo".
 *
 * <p>Nao acumula os votos: conta e descarta. Guardar as folhas seria duplicar em estado do
 * Flink o que o topico {@code votes.accepted} ja carrega.
 */
public final class WindowMarkerFunction
        extends ProcessAllWindowFunction<VoteCastEvent, WindowMarkerEvent, TimeWindow> {

    private static final long serialVersionUID = 1L;

    @Override
    public void process(Context context, Iterable<VoteCastEvent> votos, Collector<WindowMarkerEvent> out) {
        TimeWindow janela = context.window();

        long total = 0;
        for (VoteCastEvent ignorado : votos) {
            total++;
        }

        out.collect(new WindowMarkerEvent(
                WindowMarkerEvent.CURRENT_SCHEMA_VERSION,
                Instant.ofEpochMilli(janela.getStart()).toString(),
                Instant.ofEpochMilli(janela.getStart()),
                Instant.ofEpochMilli(janela.getEnd()),
                total));
    }
}
