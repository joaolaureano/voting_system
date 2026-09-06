package com.voting.benchmark.dataset;

/**
 * Municipio com um peso de sorteio.
 *
 * <p>O peso e a populacao aproximada em milhares - aproximada de proposito, porque aqui ele
 * serve so para dar formato realista a distribuicao da carga: Sao Paulo precisa receber mais
 * votos que Rorainopolis, senao a apuracao por estado e por cidade fica uniforme e nao
 * exercita chaves quentes.
 */
public record Municipio(String uf, String nome, int peso) {
}
