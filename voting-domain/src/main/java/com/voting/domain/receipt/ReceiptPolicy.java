package com.voting.domain.receipt;

import com.voting.domain.model.Vote;

/**
 * Politica de emissao do comprovante de voto.
 *
 * <p>Contrato do dominio: a emissao e <em>deterministica</em> - o mesmo voto produz sempre
 * o mesmo recibo. E isso que permite ao eleitor reconferir seu comprovante e, na proxima
 * fase, que a Merkle Tree seja reconstruida e auditada a partir dos votos.
 */
@FunctionalInterface
public interface ReceiptPolicy {

    VoteReceipt issueFor(Vote vote);
}
