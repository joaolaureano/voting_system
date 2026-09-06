package com.voting.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voting.application.port.ReceiptPublisher;
import com.voting.application.port.VoteEventPublisher;
import com.voting.domain.election.ElectionClosedException;
import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.election.WrongElectionException;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.ReceiptPolicy;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import com.voting.domain.receipt.VoteReceipt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CastVoteUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-10-04T13:00:00Z");
    private static final ElectionId ELEICAO = ElectionId.of("br-2026-presidencial");

    private final List<String> ordemDeEfeitos = new ArrayList<>();
    private final List<Vote> votosPublicados = new ArrayList<>();
    private final List<VoteReceipt> recibosPublicados = new ArrayList<>();

    private final VoteEventPublisher votePublisher = (vote, receipt) -> {
        ordemDeEfeitos.add("voto");
        votosPublicados.add(vote);
    };
    private final ReceiptPublisher receiptPublisher = (vote, receipt) -> {
        ordemDeEfeitos.add("recibo");
        recibosPublicados.add(receipt);
    };
    private final ReceiptPolicy receiptPolicy = new Sha256ReceiptPolicy("pepper-de-teste");

    private final CastVoteUseCase useCase = new CastVoteUseCase(
            ElectionSchedule.alwaysOpen(ELEICAO),
            receiptPolicy,
            votePublisher,
            receiptPublisher,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private static CastVoteCommand comando() {
        return new CastVoteCommand("br-2026-presidencial", "voter-1", "cand-1", "PT-A", "sp", "Sao Paulo");
    }

    @Test
    void publicaVotoEReciboEDevolveOComprovante() {
        CastVoteResult result = useCase.execute(comando());

        assertThat(votosPublicados).hasSize(1);
        assertThat(recibosPublicados).containsExactly(result.receipt());
        assertThat(result.receiptHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void oVotoVaiParaApuracaoAntesDoComprovante() {
        useCase.execute(comando());

        assertThat(ordemDeEfeitos).containsExactly("voto", "recibo");
    }

    // O horario e sempre o do servidor: nao existe campo para o cliente informar o seu. Com
    // prazo de encerramento, aceitar o horario do cliente permitiria antedatar um voto.
    @Test
    void carimbaSempreOHorarioDoServidor() {
        CastVoteResult result = useCase.execute(comando());

        assertThat(result.vote().castAt()).isEqualTo(AGORA);
    }

    @Test
    void promoveOsCamposPrimitivosAoModeloDeDominio() {
        Vote vote = useCase.execute(comando()).vote();

        assertThat(vote.voterId().value()).isEqualTo("voter-1");
        assertThat(vote.region().state()).isEqualTo("SP");
        assertThat(vote.electionId()).isEqualTo(ELEICAO);
    }

    @Test
    void assumeAEleicaoConfiguradaQuandoOClienteOmite() {
        CastVoteCommand semEleicao =
                new CastVoteCommand(null, "voter-1", "cand-1", "PT-A", "SP", "Sao Paulo");

        assertThat(useCase.execute(semEleicao).vote().electionId()).isEqualTo(ELEICAO);
    }

    @Test
    void recusaVotoDeOutraEleicao() {
        CastVoteCommand outra =
                new CastVoteCommand("br-2030-presidencial", "voter-1", "cand-1", "PT-A", "SP", "Sao Paulo");

        // Nomeada, e nao IllegalArgumentException: quem trata decide pelo tipo, sem ler
        // a mensagem, e a borda consegue devolver um codigo de erro proprio.
        assertThatThrownBy(() -> useCase.execute(outra))
                .isInstanceOf(WrongElectionException.class)
                .hasMessageContaining("br-2030-presidencial");
        assertThat(votosPublicados).isEmpty();
    }

    @Test
    void naoPublicaNadaQuandoOVotoEInvalido() {
        CastVoteCommand semCandidato =
                new CastVoteCommand(null, "voter-1", " ", "PT-A", "SP", "Sao Paulo");

        assertThatThrownBy(() -> useCase.execute(semCandidato)).isInstanceOf(IllegalArgumentException.class);
        assertThat(votosPublicados).isEmpty();
        assertThat(recibosPublicados).isEmpty();
    }

    @Test
    void recusaVotoDepoisDoEncerramento() {
        CastVoteUseCase encerrada = new CastVoteUseCase(
                new ElectionSchedule(ELEICAO, AGORA.minusSeconds(3600), AGORA),
                receiptPolicy, votePublisher, receiptPublisher, Clock.fixed(AGORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> encerrada.execute(comando()))
                .isInstanceOf(ElectionClosedException.class)
                .hasMessageContaining("encerrou em");
        assertThat(votosPublicados).isEmpty();
    }

    @Test
    void recusaVotoAntesDaAbertura() {
        CastVoteUseCase aindaFechada = new CastVoteUseCase(
                new ElectionSchedule(ELEICAO, AGORA.plusSeconds(60), AGORA.plusSeconds(3600)),
                receiptPolicy, votePublisher, receiptPublisher, Clock.fixed(AGORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> aindaFechada.execute(comando()))
                .isInstanceOf(ElectionClosedException.class)
                .hasMessageContaining("abre em");
        assertThat(votosPublicados).isEmpty();
    }

    @Test
    void oUltimoMilissegundoAntesDoPrazoAindaVale() {
        CastVoteUseCase noLimite = new CastVoteUseCase(
                new ElectionSchedule(ELEICAO, AGORA.minusSeconds(3600), AGORA.plusMillis(1)),
                receiptPolicy, votePublisher, receiptPublisher, Clock.fixed(AGORA, ZoneOffset.UTC));

        assertThat(noLimite.execute(comando()).receiptHash()).matches("[0-9a-f]{64}");
    }
}
