package com.voting.streaming.tally;

import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.VoteEventMapper;
import com.voting.domain.tally.TallyDimension;
import org.apache.flink.api.java.functions.KeySelector;

/**
 * Extrai do voto a chave de agregacao de uma dimensao, delegando ao dominio.
 *
 * <p>Chega aqui apenas o que ja passou pelo dedup, ou seja, votos validos - por isso a
 * conversao para o dominio nao precisa de rede de protecao neste ponto.
 */
public final class DimensionKeySelector implements KeySelector<VoteCastEvent, String> {

    private static final long serialVersionUID = 1L;

    private final TallyDimension dimension;

    public DimensionKeySelector(TallyDimension dimension) {
        this.dimension = dimension;
    }

    @Override
    public String getKey(VoteCastEvent event) {
        return dimension.keyOf(VoteEventMapper.toDomain(event));
    }
}
