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
import com.voting.ingest.kafka.EventPublicationException;
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
 * Contrato HTTP da ingestao, com o caso de uso real e portas em memoria.
 *
 * <p>Nao ha mock do caso de uso: o que se quer provar e que a borda traduz corretamente para
 * o dominio e de volta, e um dublê do caso de uso esconderia justamente essa traducao.
 */
@WebMvcTest(VoteController.class)
@Import(VoteControllerTest.PortasEmMemoria.class)
class VoteControllerTest {

    static final List<Vote> PUBLICADOS = new ArrayList<>();
    static volatile boolean kafkaIndisponivel = false;

    static final ElectionId ELEICAO = ElectionId.of("br-2026-presidencial");
    static final Instant AGORA = Instant.parse("2026-10-04T13:00:00Z");

    @TestConfiguration
    static class PortasEmMemoria {

        @Bean
        VoteEventPublisher votePublisher() {
            return (vote, receipt) -> {
                if (kafkaIndisponivel) {
                    throw new EventPublicationException("votes.cast", new IllegalStateException("broker fora"));
                }
                PUBLICADOS.add(vote);
            };
        }

        @Bean
        ReceiptPublisher receiptPublisher() {
            return (vote, receipt) -> {
            };
        }

        @Bean
        CastVoteUseCase castVoteUseCase(VoteEventPublisher votes, ReceiptPublisher receipts) {
            return new CastVoteUseCase(
                    ElectionSchedule.alwaysOpen(ELEICAO),
                    new Sha256ReceiptPolicy("pepper-de-teste"),
                    votes,
                    receipts,
                    Clock.fixed(AGORA, ZoneOffset.UTC));
        }
    }

    @Autowired
    private MockMvc mvc;

    private static final String VOTO_VALIDO = """
            {"voterId":"voter-1","candidateId":"cand-1","partyId":"PT-A",
             "state":"sp","city":"Sao Paulo"}
            """;

    @Test
    void registraVotoEDevolveOComprovante() throws Exception {
        PUBLICADOS.clear();
        kafkaIndisponivel = false;

        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON).content(VOTO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipt").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.castAt").value("2026-10-04T13:00:00Z"));

        assertThat(PUBLICADOS).singleElement()
                .satisfies(vote -> assertThat(vote.region().state()).isEqualTo("SP"));
    }

    @Test
    void mesmoVotoDevolveSempreOMesmoComprovante() throws Exception {
        kafkaIndisponivel = false;
        String primeiro = mvc.perform(post("/api/v1/votes")
                        .contentType(MediaType.APPLICATION_JSON).content(VOTO_VALIDO))
                .andReturn().getResponse().getContentAsString();
        String segundo = mvc.perform(post("/api/v1/votes")
                        .contentType(MediaType.APPLICATION_JSON).content(VOTO_VALIDO))
                .andReturn().getResponse().getContentAsString();

        assertThat(primeiro).isEqualTo(segundo);
    }

    // O horario do voto e do servidor. Mandar castAt no corpo nao muda nada - se mudasse,
    // bastaria antedatar o voto para furar o encerramento.
    @Test
    void ignoraOHorarioEnviadoPeloCliente() throws Exception {
        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voterId":"voter-9","candidateId":"cand-1","partyId":"PT-A",
                                 "state":"SP","city":"Sao Paulo","castAt":"2020-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.castAt").value("2026-10-04T13:00:00Z"));
    }

    @Test
    void recusaVotoSemCandidatoCom400() throws Exception {
        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"voterId":"voter-1","partyId":"PT-A","state":"SP","city":"Sao Paulo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VOTO_INVALIDO"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("candidateId")));
    }

    @Test
    void recusaVotoDeOutraEleicaoCom400() throws Exception {
        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"electionId":"br-2030-presidencial","voterId":"voter-1",
                                 "candidateId":"cand-1","partyId":"PT-A","state":"SP","city":"Sao Paulo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VOTO_INVALIDO"));
    }

    @Test
    void devolve503QuandoOVotoNaoChegaAoKafka() throws Exception {
        kafkaIndisponivel = true;

        mvc.perform(post("/api/v1/votes").contentType(MediaType.APPLICATION_JSON).content(VOTO_VALIDO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("REGISTRO_INDISPONIVEL"));

        kafkaIndisponivel = false;
    }
}
