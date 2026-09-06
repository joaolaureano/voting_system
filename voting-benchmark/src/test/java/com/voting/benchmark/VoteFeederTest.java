package com.voting.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VoteFeederTest {

    private static List<Map<String, Object>> colher(VoteFeeder feeder, int quantidade) {
        List<Map<String, Object>> cedulas = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            cedulas.add(feeder.next());
        }
        return cedulas;
    }

    @Test
    void mesmaSementeProduzExatamenteAMesmaSequencia() {
        List<Map<String, Object>> primeira = colher(new VoteFeeder("run", 0.1, 2026L), 500);
        List<Map<String, Object>> segunda = colher(new VoteFeeder("run", 0.1, 2026L), 500);

        assertThat(primeira).isEqualTo(segunda);
    }

    @Test
    void sementesDiferentesProduzemCargasDiferentes() {
        assertThat(colher(new VoteFeeder("run", 0.1, 2026L), 200))
                .isNotEqualTo(colher(new VoteFeeder("run", 0.1, 7L), 200));
    }

    @Test
    void cadaEleitorNovoRecebeUmIdentificadorUnicoDaRodada() {
        List<Map<String, Object>> cedulas = colher(new VoteFeeder("rodada-a", 0.0, 2026L), 1_000);

        Set<Object> ids = cedulas.stream().map(c -> c.get("voterId")).collect(Collectors.toSet());
        assertThat(ids).hasSize(1_000);
        assertThat(ids).allSatisfy(id -> assertThat((String) id).startsWith("rodada-a-"));
    }

    @Test
    void aTaxaDeDuplicatasERespeitadaECadaDuplicataEDeAlguemQueJaVotou() {
        VoteFeeder feeder = new VoteFeeder("run", 0.2, 2026L);
        List<Map<String, Object>> cedulas = colher(feeder, 10_000);

        long duplicatas = cedulas.stream().filter(c -> (boolean) c.get("duplicata")).count();
        assertThat(duplicatas).isCloseTo(2_000L, org.assertj.core.data.Offset.offset(200L));
        assertThat(feeder.eleitoresDistintos()).isEqualTo(10_000 - duplicatas);

        Set<Object> vistos = new HashSet<>();
        for (Map<String, Object> cedula : cedulas) {
            if ((boolean) cedula.get("duplicata")) {
                assertThat(vistos).contains(cedula.get("voterId"));
            } else {
                vistos.add(cedula.get("voterId"));
            }
        }
    }

    @Test
    void aIntencaoDeVotoNaoEUmEmpateEntreOsDoze() {
        List<Map<String, Object>> cedulas = colher(new VoteFeeder("run", 0.0, 2026L), 20_000);

        Map<Object, Long> porCandidato = cedulas.stream()
                .collect(Collectors.groupingBy(c -> c.get("candidateId"), Collectors.counting()));
        long maior = porCandidato.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        long menor = porCandidato.values().stream().mapToLong(Long::longValue).min().orElseThrow();

        assertThat(porCandidato).hasSize(12);
        assertThat(maior).isGreaterThan(menor * 5);
    }

    @Test
    void osVotosSeConcentramNosMunicipiosMaisPopulosos() {
        List<Map<String, Object>> cedulas = colher(new VoteFeeder("run", 0.0, 2026L), 20_000);

        Map<Object, Long> porEstado = cedulas.stream()
                .collect(Collectors.groupingBy(c -> c.get("state"), Collectors.counting()));

        assertThat(porEstado).hasSize(27);
        assertThat(porEstado.get("SP")).isGreaterThan(porEstado.get("RR"));
    }
}
