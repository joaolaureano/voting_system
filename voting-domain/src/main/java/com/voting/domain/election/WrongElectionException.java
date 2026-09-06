package com.voting.domain.election;

import com.voting.domain.DomainException;
import com.voting.domain.model.ElectionId;

/**
 * O voto foi endereçado a uma eleicao diferente da que esta instancia atende.
 *
 * <p>Distinta de {@link ElectionClosedException} porque as duas pedem acoes diferentes de quem
 * recebe: eleicao errada e erro de endereçamento do cliente, e votacao encerrada e uma resposta
 * definitiva sobre um pedido correto.
 */
public class WrongElectionException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient ElectionId expected;
    private final transient ElectionId received;

    public WrongElectionException(ElectionId expected, ElectionId received) {
        super("ELEICAO_INCORRETA",
                "voto endereçado a outra eleicao: " + received + " != " + expected);
        this.expected = expected;
        this.received = received;
    }

    public ElectionId expected() {
        return expected;
    }

    public ElectionId received() {
        return received;
    }
}
