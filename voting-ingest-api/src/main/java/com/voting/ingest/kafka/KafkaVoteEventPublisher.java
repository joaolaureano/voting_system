package com.voting.ingest.kafka;

import com.voting.application.port.VoteEventPublisher;
import com.voting.contracts.VoteEventMapper;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;
import com.voting.ingest.config.VotingProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Entrega o voto a apuracao publicando em {@code votes.cast}.
 *
 * <p>A chave e o identificador do eleitor. Isso nao e detalhe: e o que garante que os votos
 * de um mesmo eleitor caiam na mesma particao e portanto no mesmo operador de dedup do Flink.
 * Mudar essa chave quebra a regra "um voto por eleitor" sem quebrar nenhum teste unitario.
 */
@Component
public class KafkaVoteEventPublisher extends KafkaEventPublisher implements VoteEventPublisher {

    private final String topic;

    public KafkaVoteEventPublisher(KafkaTemplate<String, String> kafka, VotingProperties properties) {
        super(kafka, properties.sendTimeoutMs());
        this.topic = properties.votesTopic();
    }

    @Override
    public void publish(Vote vote, VoteReceipt receipt) {
        send(topic, vote.voterId().value(), VoteEventMapper.toEvent(vote, receipt));
    }
}
