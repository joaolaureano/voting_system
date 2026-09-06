package com.voting.ingest.config;

import com.voting.application.port.ReceiptPublisher;
import com.voting.application.port.VoteEventPublisher;
import com.voting.application.usecase.CastVoteUseCase;
import com.voting.domain.model.ElectionId;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Montagem da aplicacao: e aqui - e so aqui - que as portas ganham suas implementacoes.
 *
 * <p>O caso de uso e um objeto Java comum, sem anotacao de framework. Trocar Spring por outra
 * coisa reescreve esta classe e nada mais.
 */
@Configuration
public class VotingConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ElectionId electionId(VotingProperties properties) {
        return ElectionId.of(properties.electionId());
    }

    @Bean
    public ReceiptPolicy receiptPolicy(VotingProperties properties) {
        return new Sha256ReceiptPolicy(properties.receiptPepper());
    }

    @Bean
    public CastVoteUseCase castVoteUseCase(
            ElectionId electionId,
            ReceiptPolicy receiptPolicy,
            VoteEventPublisher votePublisher,
            ReceiptPublisher receiptPublisher,
            Clock clock) {
        return new CastVoteUseCase(electionId, receiptPolicy, votePublisher, receiptPublisher, clock);
    }
}
