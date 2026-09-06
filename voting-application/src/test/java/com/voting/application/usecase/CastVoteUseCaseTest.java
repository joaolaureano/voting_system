package com.voting.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voting.application.port.ReceiptPublisher;
import com.voting.application.port.VoteEventPublisher;
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
            ELEICAO,
            receiptPolicy,
            votePublisher,
            receiptPublisher,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private static CastVoteCommand comando() {
        return new CastVoteCommand("br-2026-presidencial", "voter-1", "cand-1", "PT-A", "sp", "Sao Paulo", null);
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

    @Test
    void carimbaOHorarioDoServidorQuandoOClienteNaoInforma() {
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
                new CastVoteCommand(null, "voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", null);

        assertThat(useCase.execute(semEleicao).vote().electionId()).isEqualTo(ELEICAO);
    }

    @Test
    void recusaVotoDeOutraEleicao() {
        CastVoteCommand outra =
                new CastVoteCommand("br-2030-presidencial", "voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", null);

        assertThatThrownBy(() -> useCase.execute(outra)).isInstanceOf(IllegalArgumentException.class);
        assertThat(votosPublicados).isEmpty();
    }

    @Test
    void naoPublicaNadaQuandoOVotoEInvalido() {
        CastVoteCommand semCandidato =
                new CastVoteCommand(null, "voter-1", " ", "PT-A", "SP", "Sao Paulo", null);

        assertThatThrownBy(() -> useCase.execute(semCandidato)).isInstanceOf(IllegalArgumentException.class);
        assertThat(votosPublicados).isEmpty();
        assertThat(recibosPublicados).isEmpty();
    }

    @Test
    void recusaVotoComHorarioNoFuturo() {
        CastVoteCommand futuro = new CastVoteCommand(
                null, "voter-1", "cand-1", "PT-A", "SP", "Sao Paulo", AGORA.plusSeconds(3600));

        assertThatThrownBy(() -> useCase.execute(futuro)).isInstanceOf(IllegalArgumentException.class);
    }
}
