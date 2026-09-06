package com.voting.contracts;

import java.time.Instant;

/**
 * Evento de controle publicado em {@code votes.control}. Nao e voto e nunca entra na apuracao.
 *
 * <p>Existe por um motivo so: a marca d'agua do Flink e derivada do dado, entao um fluxo
 * parado a congela e as janelas nunca fecham. Sem estes eventos, os ultimos votos de uma
 * eleicao ficariam para sempre sem raiz - e sem prova de inclusao para quem votou.
 *
 * <p>A alternativa - disparar a janela por tempo de processamento - resolveria o travamento e
 * destruiria a auditoria: o conteudo de cada janela passaria a depender do relogio de parede
 * durante o processamento, e dois reprocessamentos do mesmo log dariam raizes diferentes.
 * Colocando o avanco do tempo <em>no proprio log</em>, o replay reproduz tudo.
 */
public record ControlEvent(
        int schemaVersion,
        String type,
        String electionId,
        Instant at) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Batimento periodico: so empurra a marca d'agua durante periodos sem votos. */
    public static final String HEARTBEAT = "HEARTBEAT";

    /**
     * Encerramento da votacao. Carimbado depois do prazo o bastante para fechar a ultima
     * janela, e o que faz a raiz final ser publicada.
     */
    public static final String ELECTION_CLOSED = "ELECTION_CLOSED";

    public static ControlEvent heartbeat(String electionId, Instant at) {
        return new ControlEvent(CURRENT_SCHEMA_VERSION, HEARTBEAT, electionId, at);
    }

    public static ControlEvent electionClosed(String electionId, Instant at) {
        return new ControlEvent(CURRENT_SCHEMA_VERSION, ELECTION_CLOSED, electionId, at);
    }
}
