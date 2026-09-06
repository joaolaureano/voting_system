package com.voting.ingest.kafka;

import com.voting.application.port.ReceiptPublisher;
import com.voting.contracts.VoteEventMapper;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;
import com.voting.ingest.config.VotingProperties;
import java.time.Clock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Registra o comprovante em {@code votes.receipts}, com o proprio hash como chave.
 *
 * <p>Topico compactado: o ultimo registro de cada hash sobrevive indefinidamente, que e o que
 * permite ao eleitor conferir seu comprovante muito depois da eleicao.
 */
@Component
public class KafkaReceiptPublisher extends KafkaEventPublisher implements ReceiptPublisher {

    private final String topic;
    private final Clock clock;

    public KafkaReceiptPublisher(
            KafkaTemplate<String, String> kafka, VotingProperties properties, Clock clock) {
        super(kafka, properties.sendTimeoutMs());
        this.topic = properties.receiptsTopic();
        this.clock = clock;
    }

    @Override
    public void publish(Vote vote, VoteReceipt receipt) {
        send(topic, receipt.hash(), VoteEventMapper.toReceiptEvent(vote, receipt, clock.instant()));
    }
}
