package com.voting.benchmark.dataset;

/**
 * Partido registrado no TSE.
 *
 * @param numero numero da legenda (13, 22, 45...)
 * @param sigla  sigla, usada como {@code partyId} no voto e como chave em results.by-party
 * @param nome   nome por extenso
 */
public record Partido(String numero, String sigla, String nome) {
}
