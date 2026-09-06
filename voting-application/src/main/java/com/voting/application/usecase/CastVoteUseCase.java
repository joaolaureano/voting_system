package com.voting.application.usecase;

import com.voting.application.port.ReceiptPublisher;
import com.voting.application.port.VoteEventPublisher;
import com.voting.domain.election.ElectionClosedException;
import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.CandidateId;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.PartyId;
import com.voting.domain.model.Region;
import com.voting.domain.model.Vote;
import com.voting.domain.model.VoterId;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.VoteReceipt;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Registrar um voto: carimbar, validar, emitir o comprovante e entregar as duas portas.
 *
 * <p>O horario do voto e o do <em>servidor no instante da chegada</em>. Nao ha como saber o
 * momento exato do clique sem confiar no relogio do dispositivo, e confiar nele tornaria o
 * encerramento contornavel. O preco e que o carimbo inclui a latencia de rede - diferenca
 * irrelevante fora da fronteira do prazo.
 *
 * <p>A verificacao de prazo aqui e conveniencia: devolve a recusa ao eleitor na hora, em vez
 * de dar um comprovante que a apuracao vai descartar. A autoridade sobre o prazo continua
 * sendo o Flink, unico ponto que ve todos os votos com um relogio so.
 *
 * <p>A ordem das publicacoes importa. O voto vai primeiro para a apuracao e so depois o
 * comprovante e registrado: se a segunda publicacao falhar, existe um voto sem comprovante
 * consultavel - incomodo, mas recuperavel. A ordem inversa produziria um comprovante para um
 * voto que nunca entrou na apuracao, que e mentir para o eleitor.
 */
public final class CastVoteUseCase {

    private final ElectionSchedule schedule;
    private final ReceiptPolicy receiptPolicy;
    private final VoteEventPublisher votePublisher;
    private final ReceiptPublisher receiptPublisher;
    private final Clock clock;

    public CastVoteUseCase(
            ElectionSchedule schedule,
            ReceiptPolicy receiptPolicy,
            VoteEventPublisher votePublisher,
            ReceiptPublisher receiptPublisher,
            Clock clock) {
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.receiptPolicy = Objects.requireNonNull(receiptPolicy, "receiptPolicy");
        this.votePublisher = Objects.requireNonNull(votePublisher, "votePublisher");
        this.receiptPublisher = Objects.requireNonNull(receiptPublisher, "receiptPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CastVoteResult execute(CastVoteCommand command) {
        Objects.requireNonNull(command, "command");
        Instant agora = clock.instant();

        ElectionId eleicaoDoComando = command.electionId() == null
                ? schedule.election()
                : ElectionId.of(command.electionId());
        if (!schedule.election().equals(eleicaoDoComando)) {
            throw new IllegalArgumentException(
                    "voto endereçado a outra eleicao: " + eleicaoDoComando + " != " + schedule.election());
        }
        if (!schedule.isOpenAt(agora)) {
            throw new ElectionClosedException(schedule, agora);
        }

        Vote vote = Vote.cast(
                schedule.election(),
                VoterId.of(command.voterId()),
                CandidateId.of(command.candidateId()),
                PartyId.of(command.partyId()),
                Region.of(command.state(), command.city()),
                agora,
                agora);

        VoteReceipt receipt = receiptPolicy.issueFor(vote);
        votePublisher.publish(vote, receipt);
        receiptPublisher.publish(vote, receipt);

        return new CastVoteResult(receipt, vote);
    }
}
