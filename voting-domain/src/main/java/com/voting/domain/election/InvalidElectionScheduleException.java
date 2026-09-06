package com.voting.domain.election;

import com.voting.domain.DomainException;
import java.time.Instant;

/**
 * O periodo da eleicao nao descreve um intervalo valido.
 *
 * <p>Diferente das outras recusas do dominio, esta quase sempre e erro de configuracao, nao de
 * quem votou - e ela e levantada na montagem da aplicacao, antes de qualquer voto existir. E
 * exatamente por isso que vale ter nome proprio: nos logs, ela aponta para o arquivo de
 * configuracao em vez de para a requisicao.
 */
public class InvalidElectionScheduleException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidElectionScheduleException(Instant opensAt, Instant closesAt) {
        super("PERIODO_INVALIDO",
                "a eleicao fecharia antes de abrir: " + opensAt + " .. " + closesAt);
    }
}
