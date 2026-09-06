package com.voting.domain.ballot;

import com.voting.domain.receipt.VoteReceipt;
import java.util.Objects;

/**
 * Resultado da admissao de um voto na apuracao.
 *
 * <p>Sealed: o chamador e obrigado a tratar os dois desfechos, o que impede que um voto
 * rejeitado passe despercebido para dentro das contagens.
 */
public sealed interface AdmissionDecision {

    /** Voto admitido; e o primeiro deste eleitor. */
    record Accepted(VoteReceipt receipt) implements AdmissionDecision {
        public Accepted {
            Objects.requireNonNull(receipt, "receipt");
        }
    }

    /**
     * Voto recusado. Carrega o recibo do voto original quando existe, para que o eleitor
     * possa ser informado de qual comprovante de fato vale.
     */
    record Rejected(RejectionReason reason, VoteReceipt originalReceipt) implements AdmissionDecision {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    default boolean accepted() {
        return this instanceof Accepted;
    }
}
