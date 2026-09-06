package com.voting.contracts;

import java.time.Instant;

/**
 * Voto admitido na apuracao, publicado em {@code votes.accepted}.
 *
 * <p>E a entrada da Merkle Tree - por isso vem <em>depois</em> do dedup, e nao do topico de
 * comprovantes: {@code votes.receipts} recebe um registro por voto que chega na borda,
 * inclusive os que serao recusados. Uma arvore construida sobre aquele topico daria prova de
 * inclusao para votos que nunca foram contados.
 *
 * <p>Nao carrega o candidato, deliberadamente. Este topico alimenta o servico que responde
 * "meu voto entrou?" ao publico; se trouxesse o candidato, viraria um mapa do voto de cada
 * eleitor.
 */
public record AcceptedVoteEvent(
        int schemaVersion,
        String receipt,
        String voterId,
        String windowId,
        Instant castAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
