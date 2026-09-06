package com.voting.ingest.web;

import com.voting.application.usecase.CastVoteCommand;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Corpo do POST /api/v1/votes.
 *
 * <p>As anotacoes cobrem o basico de formato para devolver 400 com mensagem util antes de
 * incomodar o dominio. Elas nao substituem as invariantes do {@code Vote}: validacao de
 * borda e conveniencia, a regra continua sendo do dominio.
 *
 * @param electionId opcional; ausente significa "a eleicao que este servidor atende"
 * @param castAt     opcional; ausente faz o servidor carimbar o horario
 */
public record CastVoteRequest(
        String electionId,
        @NotBlank(message = "voterId e obrigatorio") String voterId,
        @NotBlank(message = "candidateId e obrigatorio") String candidateId,
        @NotBlank(message = "partyId e obrigatorio") String partyId,
        @NotBlank(message = "state e obrigatorio") String state,
        @NotBlank(message = "city e obrigatorio") String city,
        Instant castAt) {

    public CastVoteCommand toCommand() {
        return new CastVoteCommand(electionId, voterId, candidateId, partyId, state, city, castAt);
    }
}
