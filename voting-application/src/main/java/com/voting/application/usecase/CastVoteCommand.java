package com.voting.application.usecase;

/**
 * Intencao de voto vinda da borda, ainda em tipos primitivos.
 *
 * <p>E o caso de uso que promove esses campos a value objects do dominio. Assim o adaptador
 * HTTP nao precisa conhecer o dominio, e uma mudanca de modelagem nao vaza para o controller.
 *
 * <p><strong>Nao existe campo de horario, deliberadamente.</strong> Quem carimba o momento do
 * voto e o servidor, na chegada. Aceitar o horario do cliente seria entregar a ele a chave do
 * encerramento: bastaria antedatar o voto para entrar depois do prazo.
 */
public record CastVoteCommand(
        String electionId,
        String voterId,
        String candidateId,
        String partyId,
        String state,
        String city) {
}
