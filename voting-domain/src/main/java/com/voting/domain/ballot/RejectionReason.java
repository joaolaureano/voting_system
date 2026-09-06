package com.voting.domain.ballot;

/** Motivos pelos quais um voto pode nao entrar na apuracao. */
public enum RejectionReason {

    /** O eleitor ja tinha um voto computado nesta eleicao. */
    DUPLICATE_VOTE,

    /** O voto chegou para uma eleicao diferente da que o pipeline esta apurando. */
    WRONG_ELECTION,

    /**
     * O evento chegou bem formado como JSON, mas viola as invariantes do voto (identificador
     * vazio, regiao ausente). Vira rejeicao auditavel em vez de derrubar a apuracao.
     */
    INVALID_VOTE
}
