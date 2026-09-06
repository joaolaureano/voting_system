package com.voting.contracts;

import java.time.Instant;

/**
 * Comprovante emitido, publicado em {@code votes.receipts} com o hash como chave.
 *
 * <p>Deliberadamente <em>nao</em> carrega o candidato: e a base da consulta publica "meu
 * voto entrou?", e um evento que ligasse recibo a candidato transformaria esse topico num
 * mapa do voto de cada eleitor.
 */
public record ReceiptEvent(
        int schemaVersion,
        String receipt,
        String electionId,
        String voterId,
        Instant issuedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
