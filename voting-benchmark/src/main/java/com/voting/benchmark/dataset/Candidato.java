package com.voting.benchmark.dataset;

/**
 * Candidato da eleicao de benchmark.
 *
 * <p>O {@code numero} e tambem o {@code candidateId} enviado no voto, como na urna brasileira:
 * o eleitor digita o numero do partido do candidato a presidencia. Isso deixa as chaves de
 * {@code results.by-candidate} legiveis sem consultar outra tabela.
 */
public record Candidato(String numero, String nome, String partidoSigla) {
}
