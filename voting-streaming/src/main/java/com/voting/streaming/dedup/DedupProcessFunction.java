package com.voting.streaming.dedup;

import com.voting.contracts.RejectedVoteEvent;
import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.VoteEventMapper;
import com.voting.domain.ballot.AdmissionDecision;
import com.voting.domain.ballot.RejectionReason;
import com.voting.domain.ballot.VoteAdmission;
import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.VoteReceipt;
import com.voting.streaming.serde.JsonTypeInfo;
import java.time.Instant;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aplica "um voto por eleitor" usando o estado por chave do Flink como memoria.
 *
 * <p>Divisao de responsabilidades: o Flink fornece o <em>estado</em> (qual foi o primeiro
 * recibo deste eleitor) e o dominio toma a <em>decisao</em>. Esta funcao nao contem nenhuma
 * regra propria - trocar o Flink por outro mecanismo de estado nao muda a apuracao.
 *
 * <p>Fluxo principal: votos admitidos. Saida lateral {@link #REJECTED}: duplicatas e votos
 * invalidos, para que nada seja descartado em silencio.
 */
public final class DedupProcessFunction extends KeyedProcessFunction<String, VoteCastEvent, VoteCastEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DedupProcessFunction.class);

    public static final OutputTag<RejectedVoteEvent> REJECTED =
            new OutputTag<>("votos-rejeitados", JsonTypeInfo.REJECTED);

    private final String electionId;
    private final String opensAt;
    private final String closesAt;

    private transient VoteAdmission admission;
    private transient ValueState<String> firstReceipt;
    private transient org.apache.flink.metrics.Counter acceptedVotes;
    private transient org.apache.flink.metrics.Counter rejectedVotes;

    /**
     * Recebe a agenda desmontada em Strings porque a funcao e serializada e enviada aos
     * TaskManagers; remontar na abertura evita depender da serializacao dos tipos do dominio.
     */
    public DedupProcessFunction(ElectionSchedule schedule) {
        this.electionId = schedule.election().value();
        this.opensAt = schedule.opensAt().toString();
        this.closesAt = schedule.closesAt().toString();
    }

    @Override
    public void open(Configuration parameters) {
        admission = new VoteAdmission(new ElectionSchedule(
                com.voting.domain.model.ElectionId.of(electionId),
                java.time.Instant.parse(opensAt),
                java.time.Instant.parse(closesAt)));
        // O estado guarda o hash, e nao o VoteReceipt: uma String tem serializador nativo no
        // Flink e mantem o savepoint legivel sem depender das classes do dominio.
        firstReceipt = getRuntimeContext()
                .getState(new ValueStateDescriptor<>("primeiro-recibo-do-eleitor", Types.STRING));
        acceptedVotes = getRuntimeContext().getMetricGroup().counter("acceptedVotes");
        rejectedVotes = getRuntimeContext().getMetricGroup().counter("rejectedVotes");
    }

    @Override
    public void processElement(VoteCastEvent event, Context ctx, Collector<VoteCastEvent> out)
            throws Exception {
        Instant at = Instant.ofEpochMilli(
                ctx.timestamp() != null ? ctx.timestamp() : ctx.timerService().currentProcessingTime());

        Vote vote;
        VoteReceipt receipt;
        try {
            vote = VoteEventMapper.toDomain(event);
            receipt = VoteEventMapper.receiptOf(event);
        } catch (RuntimeException e) {
            LOG.warn("voto invalido rejeitado (eleitor {}): {}", event.voterId(), e.toString());
            reject(ctx, event, new AdmissionDecision.Rejected(RejectionReason.INVALID_VOTE, null), at);
            return;
        }

        String previous = firstReceipt.value();
        AdmissionDecision decision =
                admission.admit(vote, receipt, previous == null ? null : VoteReceipt.of(previous));

        if (decision instanceof AdmissionDecision.Accepted accepted) {
            firstReceipt.update(accepted.receipt().hash());
            acceptedVotes.inc();
            out.collect(event);
        } else if (decision instanceof AdmissionDecision.Rejected rejection) {
            reject(ctx, event, rejection, at);
        }
    }

    private void reject(
            Context ctx, VoteCastEvent event, AdmissionDecision.Rejected rejection, Instant at) {
        rejectedVotes.inc();
        ctx.output(REJECTED, VoteEventMapper.toRejectedEvent(event, rejection, at));
    }
}
