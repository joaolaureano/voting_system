package com.voting.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.voting.benchmark.dataset.Candidato;
import com.voting.benchmark.dataset.CandidatosGenerator;
import com.voting.benchmark.dataset.Dataset;
import com.voting.benchmark.dataset.Municipio;
import com.voting.benchmark.dataset.Partido;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guarda-corpo da base fixa. Se um destes testes quebrar, o benchmark deixou de ser
 * comparavel com as rodadas anteriores - o que pode ser intencional, mas nunca acidental.
 */
class DatasetTest {

    @Test
    void carregaOsPartidosDoTse() {
        List<Partido> partidos = Dataset.partidos();

        assertThat(partidos).hasSize(30);
        assertThat(partidos).extracting(Partido::sigla).contains("PT", "PL", "MDB", "NOVO", "PSOL");
        assertThat(partidos).extracting(Partido::numero).doesNotHaveDuplicates();
    }

    @Test
    void cadaCandidatoTemSeuProprioPartido() {
        List<Candidato> candidatos = Dataset.candidatos();

        assertThat(candidatos).hasSize(12);
        // O numero do candidato e o numero da legenda: dois candidatos do mesmo partido
        // colidiriam na mesma chave de results.by-candidate.
        assertThat(candidatos).extracting(Candidato::partidoSigla).doesNotHaveDuplicates();
        assertThat(candidatos).extracting(Candidato::numero).doesNotHaveDuplicates();
    }

    @Test
    void osNumerosDosCandidatosExistemEntreOsPartidos() {
        List<String> numerosDePartido = Dataset.partidos().stream().map(Partido::numero).toList();

        assertThat(Dataset.candidatos()).allSatisfy(candidato ->
                assertThat(numerosDePartido).contains(candidato.numero()));
    }

    @Test
    void nenhumCampoDaBaseQuebraOCsvOuAChaveDoKafka() {
        assertThat(Dataset.candidatos()).allSatisfy(c -> {
            assertThat(c.nome()).doesNotContain(",").isNotBlank();
            assertThat(c.partidoSigla()).doesNotContain(",").isNotBlank();
        });
        assertThat(Dataset.municipios()).allSatisfy(m -> assertThat(m.nome()).doesNotContain(","));
    }

    @Test
    void cobreOsVinteESeteEstados() {
        List<Municipio> municipios = Dataset.municipios();

        assertThat(municipios.stream().map(Municipio::uf).distinct()).hasSize(27);
        assertThat(municipios).hasSizeGreaterThan(80);
        assertThat(municipios).allSatisfy(m -> assertThat(m.peso()).isPositive());
    }

    @Test
    void regerarABaseProduzExatamenteOsMesmosCandidatos() {
        assertThat(CandidatosGenerator.gerar(Dataset.partidos()))
                .containsExactlyElementsOf(Dataset.candidatos());
    }
}
