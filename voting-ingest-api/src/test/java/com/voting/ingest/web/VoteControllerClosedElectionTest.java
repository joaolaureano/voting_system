package com.voting.ingest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voting.application.port.ReceiptPublisher;
import com.voting.application.port.VoteEventPublisher;
import com.voting.application.usecase.CastVoteUseCase;
import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.ElectionId;
import com.voting.domain.model.Vote;
import com.voting.domain.receipt.Sha256ReceiptPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A borda com a votacao ja encerrada.
 *
 * <p>Contexto proprio, e nao uma agenda mutavel compartilhada: a data de encerramento e
 * decidida na montagem da aplicacao, e um teste que a troca em tempo de execucao estaria
 * testando um cenario que nao existe em producao.
 */
@WebMvcTest(VoteController.class)
@Import(VoteControllerClosedElectionTest.VotacaoEncerrada.class)
class VoteControllerClosedElectionTest {

    private static final Instant AGORA = Instant.parse("2026-10-04T21:00:00Z");
    private static final Instant FECHOU = Instant.parse("2026-10-04T20:00:00Z");
    static final List<Vote> PUBLICADOS = new ArrayList<>();

    @TestConfiguration
    static class VotacaoEncerrada {

        @Bean
        VoteEventPublisher votePublisher() {
            return (vote, receipt) -> PUBLICADOS.add(vote);
        }

        @Bean
        ReceiptPublisher receiptPublisher() {
            return (vote, receipt) -> {
            };
        }

        @Bean
        CastVoteUseCase castVoteUseCase(VoteEventPublisher votes, ReceiptPublisher receipts) {
            ElectionId eleicao = ElectionId.of("br-2026-presidencial");
            return new CastVoteUseCase(
                    new ElectionSchedule(eleicao, FECHOU.minusSeconds(32_400), FECHOU),
                    new Sha256ReceiptPolicy("pepper-de-teste"),
                    votes,
                    receipts,
                    Clock.fixed(AGORA, ZoneOffset.UTC));
        }
    }

    @Autowired
    private MockMvc mvc;

    private static final String VOTO = """
            {"voterId":"voter-1","candidateId":"cand-1","partyId":"PT-A",
             "state":"SP","city":"Sao Paulo"}
            """;

    @Test
    void recusaComCode403ENaoPublicaNada() throws Exception {
        PUBLICADOS.clear();

        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON).content(VOTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("VOTACAO_FECHADA"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("encerrou em")));

        assertThat(PUBLICADOS).isEmpty();
    }

    // Antedatar nao ajuda: o campo nao existe no contrato e o servidor carimba o proprio
    // horario. E o que impede que o encerramento seja contornavel pelo cliente.
    @Test
    void antedatarOVotoNaoFuraOEncerramento() throws Exception {
        PUBLICADOS.clear();

        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voterId":"voter-1","candidateId":"cand-1","partyId":"PT-A",
                                 "state":"SP","city":"Sao Paulo","castAt":"2026-10-04T12:00:00Z"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(PUBLICADOS).isEmpty();
    }
}
