package com.voting.benchmark;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * Carga de votacao contra a API de ingestao.
 *
 * <pre>
 * make bench
 * make bench BENCH="-Dvotes=50000 -Dramp=60 -DduplicateRate=0.1"
 * </pre>
 *
 * <p>Cada usuario virtual e um eleitor: uma requisicao e sai. Nao ha think time nem sessao,
 * porque e isso que uma votacao e - uma rajada de acoes unicas e independentes.
 *
 * <p>O que este teste mede e a borda de ingestao (HTTP + ack do Kafka). A correcao da
 * apuracao se confere depois, nos topicos: a soma de {@code results.by-candidate} tem de ser
 * igual ao numero de eleitores distintos, e nao ao numero de requisicoes.
 */
public class VoteSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8081");
    private static final int VOTES = Integer.getInteger("votes", 10_000);
    private static final int RAMP_SECONDS = Integer.getInteger("ramp", 30);
    private static final double DUPLICATE_RATE = Double.parseDouble(System.getProperty("duplicateRate", "0.1"));
    private static final long SEED = Long.getLong("seed", 2026L);
    private static final int P95_MAX_MS = Integer.getInteger("p95MaxMs", 1_000);

    /**
     * Namespace dos eleitores desta rodada. O default muda a cada execucao de proposito: o
     * dedup do Flink e permanente, entao reutilizar identificadores faria a segunda rodada
     * medir o caminho da rejeicao, e nao o da apuracao. Fixe com {@code -DrunId=...} apenas
     * quando quiser exatamente esse cenario.
     */
    private static final String RUN_ID =
            System.getProperty("runId", "bench" + (System.currentTimeMillis() / 1000));

    private final VoteFeeder feeder = new VoteFeeder(RUN_ID, DUPLICATE_RATE, SEED);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    private final ScenarioBuilder votacao = scenario("votacao")
            .feed(feeder)
            .exec(http("POST /api/v1/votes")
                    .post("/api/v1/votes")
                    .body(StringBody("""
                            {"voterId":"#{voterId}","candidateId":"#{candidateId}",\
                            "partyId":"#{partyId}","state":"#{state}","city":"#{city}"}"""))
                    // 201 sempre: a borda aceita o voto, e a duplicata so e recusada adiante,
                    // na apuracao. Um 503 aqui significa que o Kafka nao confirmou.
                    .check(status().is(201)));

    public VoteSimulation() {
        System.out.printf(
                "benchmark: %d requisicoes em %ds, duplicateRate=%.2f, runId=%s, seed=%d -> %s%n",
                VOTES, RAMP_SECONDS, DUPLICATE_RATE, RUN_ID, SEED, BASE_URL);

        setUp(votacao.injectOpen(rampUsers(VOTES).during(Duration.ofSeconds(RAMP_SECONDS))))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        global().responseTime().percentile3().lt(P95_MAX_MS));
    }

    @Override
    public void after() {
        long distintos = feeder.eleitoresDistintos();
        System.out.printf(
                "%n  eleitores distintos: %d%n  duplicatas enviadas: %d%n"
                        + "  a soma de results.by-candidate deve ser %d (make results)%n"
                        + "  votes.rejected deve ganhar %d registros (make rejected)%n",
                distintos, VOTES - distintos, distintos, VOTES - distintos);
    }
}
