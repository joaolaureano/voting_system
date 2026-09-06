package com.voting.benchmark.dataset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;
import net.datafaker.Faker;

/**
 * Gera {@code candidatos.csv} a partir de {@code partidos.csv}, com nomes do DataFaker.
 *
 * <p>Roda uma vez e o resultado e versionado - nao faz parte da execucao do benchmark. A
 * semente e fixa, entao regerar a base produz exatamente o mesmo arquivo: se a lista de
 * partidos mudar, da para ver no diff quem entrou e quem saiu, em vez de um arquivo inteiro
 * embaralhado.
 *
 * <pre>mvn -q -pl voting-benchmark exec:java -Dexec.mainClass=com.voting.benchmark.dataset.CandidatosGenerator</pre>
 */
public final class CandidatosGenerator {

    /** Trocar a semente reescreve a base inteira; so faca isso deliberadamente. */
    private static final long SEED = 2026L;

    /** Uma eleicao presidencial brasileira costuma ter em torno de uma duzia de candidatos. */
    private static final int QUANTIDADE = 12;

    private CandidatosGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path destino = Path.of(args.length > 0
                ? args[0]
                : "voting-benchmark/src/main/resources/dataset/candidatos.csv");

        List<Candidato> candidatos = gerar(Dataset.partidos());

        String csv = candidatos.stream()
                .map(c -> String.join(",", c.numero(), c.nome(), c.partidoSigla()))
                .collect(Collectors.joining("\n", "numero,nome,partido\n", "\n"));
        Files.writeString(destino, csv, StandardCharsets.UTF_8);

        System.out.println(candidatos.size() + " candidatos -> " + destino);
        candidatos.forEach(c -> System.out.printf("  %-3s %-28s %s%n", c.numero(), c.nome(), c.partidoSigla()));
    }

    /**
     * Sorteia partidos distintos e da um nome a cada candidato.
     *
     * <p>Um partido por candidato: como o numero do candidato e o numero da legenda, dois
     * candidatos do mesmo partido colidiriam na mesma chave de apuracao.
     */
    public static List<Candidato> gerar(List<Partido> partidos) {
        Random random = new Random(SEED);
        Faker faker = new Faker(new Locale("pt", "BR"), new Random(SEED));

        List<Partido> embaralhados = new ArrayList<>(partidos);
        java.util.Collections.shuffle(embaralhados, random);

        List<Candidato> candidatos = new ArrayList<>();
        for (Partido partido : embaralhados.subList(0, Math.min(QUANTIDADE, embaralhados.size()))) {
            String nome = normalizar(faker.name().firstName() + " " + faker.name().lastName());
            candidatos.add(new Candidato(partido.numero(), nome, partido.sigla()));
        }
        candidatos.sort(java.util.Comparator.comparingInt(c -> Integer.parseInt(c.numero())));
        return List.copyOf(candidatos);
    }

    /** Sem virgulas e sem acentos: o CSV e lido por um parser minimo e vira chave no Kafka. */
    private static String normalizar(String nome) {
        String semAcento = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.replace(",", " ").replaceAll("\\s+", " ").trim();
    }
}
