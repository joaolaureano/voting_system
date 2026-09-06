package com.voting.application.port;

import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;

/**
 * Porta de saida: entrega o voto ao fluxo de apuracao.
 *
 * <p>A porta fala a linguagem do dominio. Que do outro lado exista um topico Kafka, uma fila
 * ou uma tabela e assunto do adaptador - o caso de uso nao sabe e nao deve saber.
 */
public interface VoteEventPublisher {

    void publish(Vote vote, VoteReceipt receipt);
}
