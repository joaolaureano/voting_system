package com.voting.benchmark.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Base fixa do benchmark, versionada em {@code src/main/resources/dataset}.
 *
 * <p>Ela e versionada, e nao sorteada a cada execucao, por um motivo simples: duas rodadas de
 * carga so sao comparaveis se disputarem a mesma eleicao. Com os mesmos 12 candidatos, os
 * mesmos partidos e os mesmos municipios, uma diferenca de latencia ou de vazao entre rodadas
 * e atribuivel ao sistema, e nao ao dado.
 */
public final class Dataset {

    private static final String PARTIDOS = "/dataset/partidos.csv";
    private static final String CANDIDATOS = "/dataset/candidatos.csv";
    private static final String MUNICIPIOS = "/dataset/municipios.csv";

    private Dataset() {
    }

    public static List<Partido> partidos() {
        return ler(PARTIDOS, campos -> new Partido(campos[0], campos[1], campos[2]));
    }

    public static List<Candidato> candidatos() {
        return ler(CANDIDATOS, campos -> new Candidato(campos[0], campos[1], campos[2]));
    }

    public static List<Municipio> municipios() {
        return ler(MUNICIPIOS, campos -> new Municipio(campos[0], campos[1], Integer.parseInt(campos[2])));
    }

    /** Leitor de CSV proposital e minimo: a base nao tem virgulas nem aspas dentro dos campos. */
    private static <T> List<T> ler(String recurso, java.util.function.Function<String[], T> mapper) {
        try (InputStream in = Dataset.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("base do benchmark ausente no classpath: " + recurso);
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<T> registros = new ArrayList<>();
                reader.readLine(); // cabecalho
                String linha;
                while ((linha = reader.readLine()) != null) {
                    if (!linha.isBlank()) {
                        registros.add(mapper.apply(linha.split(",", -1)));
                    }
                }
                return List.copyOf(registros);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + recurso, e);
        }
    }
}
