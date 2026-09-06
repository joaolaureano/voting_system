package com.voting.streaming.tally;

import com.voting.contracts.TallyUpdateEvent;
import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.VoteEventMapper;
import com.voting.domain.tally.TallyDimension;
import com.voting.domain.tally.VoteTally;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * Contagem corrente de uma dimensao (candidato, estado, cidade ou partido).
 *
 * <p>Uma instancia por dimensao, sempre com o mesmo codigo: a diferenca entre "somar por
 * cidade" e "somar por partido" esta em {@link TallyDimension}, no dominio, e nao aqui.
 *
 * <p>Emite a cada voto. Em uma eleicao real isso e volumoso demais para o consumidor final; o
 * ajuste natural e agregar por janela antes de publicar - a mesma janela que a proxima fase
 * usara para a Merkle Tree.
 */
public final class TallyProcessFunction extends KeyedProcessFunction<String, VoteCastEvent, TallyUpdateEvent> {

    private static final long serialVersionUID = 1L;

    private final TallyDimension dimension;

    private transient ValueState<Long> count;

    public TallyProcessFunction(TallyDimension dimension) {
        this.dimension = dimension;
    }

    @Override
    public void open(Configuration parameters) {
        count = getRuntimeContext()
                .getState(new ValueStateDescriptor<>("apuracao-" + dimension.name().toLowerCase(), Types.LONG));
    }

    @Override
    public void processElement(VoteCastEvent event, Context ctx, Collector<TallyUpdateEvent> out)
            throws Exception {
        long anterior = count.value() == null ? 0L : count.value();

        VoteTally tally = new VoteTally(dimension, ctx.getCurrentKey(), anterior, event.castAt())
                .increment(event.castAt());

        count.update(tally.count());
        out.collect(VoteEventMapper.toTallyEvent(tally));
    }
}
