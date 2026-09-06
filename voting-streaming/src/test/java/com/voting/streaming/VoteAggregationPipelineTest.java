package com.voting.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.voting.contracts.RejectedVoteEvent;
import com.voting.contracts.TallyUpdateEvent;
import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.VoteEventMapper;
import com.voting.domain.model.CandidateId;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.PartyId;
import com.voting.domain.model.Region;
import com.voting.domain.model.Vote;
import com.voting.domain.model.VoterId;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import com.voting.domain.tally.TallyDimension;
import com.voting.streaming.serde.JsonTypeInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;

/**
 * Exercita a topologia real (dedup + apuracao) num MiniCluster, com fonte em memoria.
 *
 * <p>Sem Kafka de proposito: o que precisa de prova aqui e a semantica do pipeline. A ponta
 * com o Kafka e verificada no roteiro fim a fim do README.
 */
class VoteAggregationPipelineTest {

    private static final String ELECTION = "br-2026-presidencial";
    private static final Instant T0 = Instant.parse("2026-10-04T13:00:00Z");
    private static final ReceiptPolicy RECEIPTS = new Sha256ReceiptPolicy("pepper-de-teste");

    private static VoteCastEvent voto(
            String voterId, String candidateId, String party, String state, String city, long segundos) {
        Vote vote = new Vote(
                ElectionId.of(ELECTION),
                VoterId.of(voterId),
                CandidateId.of(candidateId),
                PartyId.of(party),
                Region.of(state, city),
                T0.plusSeconds(segundos));
        return VoteEventMapper.toEvent(vote, RECEIPTS.issueFor(vote));
    }

    private static StreamExecutionEnvironment env() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        return env;
    }

    private static <T> List<T> colher(DataStream<T> stream) throws Exception {
        List<T> out = new ArrayList<>();
        try (CloseableIterator<T> it = stream.executeAndCollect()) {
            it.forEachRemaining(out::add);
        }
        return out;
    }

    @Test
    void contaUmVotoPorEleitorEmCadaDimensao() throws Exception {
        StreamExecutionEnvironment env = env();
        DataStream<VoteCastEvent> votos = env.fromData(
                        JsonTypeInfo.VOTE_CAST,
                        voto("voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", 0),
                        voto("voter-2", "cand-1", "PT-A", "SP", "Campinas", 1),
                        voto("voter-3", "cand-2", "PT-B", "MG", "Belo Horizonte", 2))
                .setParallelism(1);

        SingleOutputStreamOperator<VoteCastEvent> admitidos = VoteAggregationJob.dedup(votos, ELECTION);
        List<TallyUpdateEvent> apuracao = colher(VoteAggregationJob.tally(admitidos, TallyDimension.CANDIDATE));

        assertThat(contagemFinal(apuracao)).containsExactlyInAnyOrderEntriesOf(
                Map.of("cand-1", 2L, "cand-2", 1L));
    }

    @Test
    void segundoVotoDoMesmoEleitorNaoEntraNaContagemEViraRejeicao() throws Exception {
        StreamExecutionEnvironment env = env();
        DataStream<VoteCastEvent> votos = env.fromData(
                        JsonTypeInfo.VOTE_CAST,
                        voto("voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", 0),
                        voto("voter-1", "cand-2", "PT-B", "SP", "Sao Paulo", 1),
                        voto("voter-2", "cand-2", "PT-B", "SP", "Sao Paulo", 2))
                .setParallelism(1);

        SingleOutputStreamOperator<VoteCastEvent> admitidos = VoteAggregationJob.dedup(votos, ELECTION);
        List<TallyUpdateEvent> apuracao = colher(VoteAggregationJob.tally(admitidos, TallyDimension.CANDIDATE));

        assertThat(contagemFinal(apuracao)).containsExactlyInAnyOrderEntriesOf(
                Map.of("cand-1", 1L, "cand-2", 1L));
    }

    @Test
    void oVotoRecusadoEPublicadoComOReciboQueVale() throws Exception {
        StreamExecutionEnvironment env = env();
        VoteCastEvent primeiro = voto("voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", 0);
        VoteCastEvent duplicado = voto("voter-1", "cand-2", "PT-B", "SP", "Sao Paulo", 1);

        SingleOutputStreamOperator<VoteCastEvent> admitidos =
                VoteAggregationJob.dedup(env.fromData(JsonTypeInfo.VOTE_CAST, primeiro, duplicado)
                        .setParallelism(1), ELECTION);

        List<RejectedVoteEvent> rejeitados =
                colher(admitidos.getSideOutput(com.voting.streaming.dedup.DedupProcessFunction.REJECTED));

        assertThat(rejeitados).singleElement().satisfies(r -> {
            assertThat(r.reason()).isEqualTo("DUPLICATE_VOTE");
            assertThat(r.receipt()).isEqualTo(duplicado.receipt());
            assertThat(r.originalReceipt()).isEqualTo(primeiro.receipt());
        });
    }

    @Test
    void votoDeOutraEleicaoNaoEntraNaApuracao() throws Exception {
        StreamExecutionEnvironment env = env();
        VoteCastEvent deOutraEleicao = new VoteCastEvent(
                1, "br-2030-presidencial", "voter-9", "cand-1", "PT-A", "SP", "Sao Paulo",
                T0, voto("voter-9", "cand-1", "PT-A", "SP", "Sao Paulo", 0).receipt());

        SingleOutputStreamOperator<VoteCastEvent> admitidos = VoteAggregationJob.dedup(
                env.fromData(JsonTypeInfo.VOTE_CAST, deOutraEleicao).setParallelism(1), ELECTION);
        List<RejectedVoteEvent> rejeitados =
                colher(admitidos.getSideOutput(com.voting.streaming.dedup.DedupProcessFunction.REJECTED));

        assertThat(rejeitados).singleElement()
                .satisfies(r -> assertThat(r.reason()).isEqualTo("WRONG_ELECTION"));
    }

    @Test
    void eventoInvalidoViraRejeicaoEmVezDeDerrubarOJob() throws Exception {
        StreamExecutionEnvironment env = env();
        VoteCastEvent semCidade = new VoteCastEvent(
                1, ELECTION, "voter-1", "cand-1", "PT-A", "SP", "  ", T0, "0".repeat(64));

        SingleOutputStreamOperator<VoteCastEvent> admitidos = VoteAggregationJob.dedup(
                env.fromData(JsonTypeInfo.VOTE_CAST, semCidade).setParallelism(1), ELECTION);
        List<RejectedVoteEvent> rejeitados =
                colher(admitidos.getSideOutput(com.voting.streaming.dedup.DedupProcessFunction.REJECTED));

        assertThat(rejeitados).singleElement()
                .satisfies(r -> assertThat(r.reason()).isEqualTo("INVALID_VOTE"));
    }

    @Test
    void cidadesHomonimasEmEstadosDiferentesNaoSeMisturam() throws Exception {
        StreamExecutionEnvironment env = env();
        DataStream<VoteCastEvent> votos = env.fromData(
                        JsonTypeInfo.VOTE_CAST,
                        voto("voter-1", "cand-1", "PT-A", "MG", "Bom Jesus", 0),
                        voto("voter-2", "cand-1", "PT-A", "RS", "Bom Jesus", 1))
                .setParallelism(1);

        List<TallyUpdateEvent> apuracao =
                colher(VoteAggregationJob.tally(VoteAggregationJob.dedup(votos, ELECTION), TallyDimension.CITY));

        assertThat(contagemFinal(apuracao)).containsExactlyInAnyOrderEntriesOf(
                Map.of("MG/Bom Jesus", 1L, "RS/Bom Jesus", 1L));
    }

    @Test
    void agregaPorEstadoEPorPartido() throws Exception {
        StreamExecutionEnvironment env = env();
        DataStream<VoteCastEvent> votos = env.fromData(
                        JsonTypeInfo.VOTE_CAST,
                        voto("voter-1", "cand-1", "PT-A", "sp", "Sao Paulo", 0),
                        voto("voter-2", "cand-2", "PT-A", "SP", "Campinas", 1),
                        voto("voter-3", "cand-3", "PT-B", "MG", "Uberlandia", 2))
                .setParallelism(1);

        SingleOutputStreamOperator<VoteCastEvent> admitidos = VoteAggregationJob.dedup(votos, ELECTION);

        assertThat(contagemFinal(colher(VoteAggregationJob.tally(admitidos, TallyDimension.STATE))))
                .containsExactlyInAnyOrderEntriesOf(Map.of("SP", 2L, "MG", 1L));
    }

    /** Ultima contagem publicada de cada chave - o mesmo que um consumidor compactado veria. */
    private static Map<String, Long> contagemFinal(List<TallyUpdateEvent> eventos) {
        return eventos.stream().collect(Collectors.toMap(
                TallyUpdateEvent::key,
                TallyUpdateEvent::count,
                (a, b) -> Math.max(a, b)));
    }
}
