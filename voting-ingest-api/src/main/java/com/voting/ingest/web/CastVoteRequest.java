package com.voting.ingest.web;

import com.voting.application.usecase.CastVoteCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo do POST /api/v1/votes.
 *
 * <p>As anotacoes cobrem o basico de formato para devolver 400 com mensagem util antes de
 * incomodar o dominio. Elas nao substituem as invariantes do {@code Vote}: validacao de
 * borda e conveniencia, a regra continua sendo do dominio.
 *
 * <p><strong>Nao ha campo de horario.</strong> O momento do voto e carimbado pelo servidor na
 * chegada; um campo aqui seria uma forma de antedatar o voto e furar o encerramento. Se o
 * cliente enviar {@code castAt} assim mesmo, ele e ignorado.
 *
 * @param electionId opcional; ausente significa "a eleicao que este servidor atende"
 */
public record CastVoteRequest(
        String electionId,
        @NotBlank(message = "voterId e obrigatorio") String voterId,
        @NotBlank(message = "candidateId e obrigatorio") String candidateId,
        @NotBlank(message = "partyId e obrigatorio") String partyId,
        @NotBlank(message = "state e obrigatorio") String state,
        @NotBlank(message = "city e obrigatorio") String city) {

    public CastVoteCommand toCommand() {
        return new CastVoteCommand(electionId, voterId, candidateId, partyId, state, city);
    }
}
