package com.voting.application.port;

import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;

/**
 * Porta de saida: registra o comprovante para consulta posterior pelo eleitor.
 *
 * <p>Separada de {@link VoteEventPublisher} porque as duas escritas tem publicos e ciclos de
 * vida distintos - a apuracao consome votos, e o eleitor consulta comprovantes.
 */
public interface ReceiptPublisher {

    void publish(Vote vote, VoteReceipt receipt);
}
