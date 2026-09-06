package com.voting.domain.tally;

import com.voting.domain.model.Vote;
import java.util.function.Function;

/**
 * As dimensoes pelas quais a apuracao e agregada.
 *
 * <p>Cada dimensao sabe extrair sua propria chave de um voto. E isso que mantem o job Flink
 * generico: ele itera sobre as dimensoes e nao contem nenhuma regra de "como se agrupa por
 * cidade". Adicionar uma dimensao nova (faixa etaria, zona eleitoral) e adicionar uma
 * constante aqui.
 */
public enum TallyDimension {

    CANDIDATE("results.by-candidate", vote -> vote.candidateId().value()),
    STATE("results.by-state", vote -> vote.region().state()),
    CITY("results.by-city", vote -> vote.region().cityKey()),
    PARTY("results.by-party", vote -> vote.partyId().value());

    private final String topic;
    private final Function<Vote, String> keyExtractor;

    TallyDimension(String topic, Function<Vote, String> keyExtractor) {
        this.topic = topic;
        this.keyExtractor = keyExtractor;
    }

    /** Chave de agregacao deste voto nesta dimensao. */
    public String keyOf(Vote vote) {
        return keyExtractor.apply(vote);
    }

    /**
     * Topico Kafka onde a apuracao desta dimensao e publicada. Convive no dominio por ser
     * parte do contrato publico da apuracao, e nao configuracao de infraestrutura - os
     * enderecos de broker, esses sim, ficam na borda.
     */
    public String topic() {
        return topic;
    }
}
