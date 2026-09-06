package com.voting.benchmark;

import com.voting.benchmark.dataset.Candidato;
import com.voting.benchmark.dataset.Dataset;
import com.voting.benchmark.dataset.Municipio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fonte de votos do benchmark: sequencia deterministica sobre a base fixa.
 *
 * <p>Deterministico significa que, para a mesma semente, a n-esima cedula e sempre a mesma -
 * mesmo candidato, mesmo municipio, mesma posicao das duplicatas. Quem consome cada cedula
 * varia com o escalonamento das threads, mas isso nao afeta nenhum agregado: o total por
 * candidato, por estado, por cidade e por partido e identico entre execucoes. E o que permite
 * comparar duas rodadas e atribuir a diferenca ao sistema.
 *
 * <p>O {@code voterId} carrega o identificador da rodada. Sem isso, uma segunda execucao
 * contra o mesmo cluster teria todos os votos recusados como duplicata - o dedup do Flink
 * lembra dos eleitores da rodada anterior.
 */
final class VoteFeeder implements Iterator<Map<String, Object>> {

    /**
     * Intencao de voto por posicao na lista de candidatos. Uma eleicao real nao e um empate
     * de doze vias: com pesos, a apuracao tem chaves quentes e chaves frias, que e o que
     * estressa o particionamento.
     */
    private static final int[] PESO_DA_INTENCAO = {28, 24, 12, 9, 7, 6, 4, 3, 3, 2, 1, 1};

    private final List<Candidato> candidatos = Dataset.candidatos();
    private final List<Municipio> municipios = Dataset.municipios();
    private final int[] acumuladoCandidatos;
    private final int[] acumuladoMunicipios;

    private final String runId;
    private final double duplicateRate;
    private final Random random;

    /** Eleitores ja usados, para que uma duplicata seja de fato um segundo voto de alguem. */
    private final List<String> emitidos = new ArrayList<>();

    private long proximoEleitor = 1;

    VoteFeeder(String runId, double duplicateRate, long seed) {
        this.runId = runId;
        this.duplicateRate = duplicateRate;
        this.random = new Random(seed);
        this.acumuladoCandidatos = acumular(pesosDosCandidatos());
        this.acumuladoMunicipios = acumular(municipios.stream().mapToInt(Municipio::peso).toArray());
    }

    private int[] pesosDosCandidatos() {
        int[] pesos = new int[candidatos.size()];
        for (int i = 0; i < pesos.length; i++) {
            pesos[i] = i < PESO_DA_INTENCAO.length ? PESO_DA_INTENCAO[i] : 1;
        }
        return pesos;
    }

    private static int[] acumular(int[] pesos) {
        int[] acumulado = new int[pesos.length];
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += pesos[i];
            acumulado[i] = soma;
        }
        return acumulado;
    }

    private int sorteia(int[] acumulado) {
        int alvo = random.nextInt(acumulado[acumulado.length - 1]) + 1;
        int indice = java.util.Arrays.binarySearch(acumulado, alvo);
        return indice >= 0 ? indice : -indice - 1;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    /** Sincronizado: os usuarios virtuais do Gatling puxam do mesmo feeder em paralelo. */
    @Override
    public synchronized Map<String, Object> next() {
        boolean duplicata = !emitidos.isEmpty() && random.nextDouble() < duplicateRate;

        String voterId;
        if (duplicata) {
            voterId = emitidos.get(random.nextInt(emitidos.size()));
        } else {
            voterId = runId + "-" + proximoEleitor++;
            emitidos.add(voterId);
        }

        Candidato candidato = candidatos.get(sorteia(acumuladoCandidatos));
        Municipio municipio = municipios.get(sorteia(acumuladoMunicipios));

        Map<String, Object> cedula = new HashMap<>();
        cedula.put("voterId", voterId);
        cedula.put("candidateId", candidato.numero());
        cedula.put("partyId", candidato.partidoSigla());
        cedula.put("state", municipio.uf());
        cedula.put("city", municipio.nome());
        cedula.put("duplicata", duplicata);
        return cedula;
    }

    /** Quantos eleitores distintos ja votaram - o total que a apuracao deve mostrar. */
    synchronized long eleitoresDistintos() {
        return proximoEleitor - 1;
    }
}
