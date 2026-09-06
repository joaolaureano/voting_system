package com.voting.ingest.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.voting.contracts.EventJson;
import com.voting.domain.election.ElectionSchedule;
import com.voting.domain.model.ElectionId;
import com.voting.ingest.config.VotingProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * O carimbo da sentinela de encerramento.
 *
 * <p>O agendador dispara em algum ponto dentro do seu intervalo, entao carimbar "agora" faria
 * a sentinela ter um horario diferente a cada execucao - e esse horario entra no log, que e a
 * fonte a partir da qual as raizes da Merkle Tree sao reproduzidas.
 */
class ControlEventPublisherTest {

    private static final Instant FECHA = Instant.parse("2026-10-04T20:00:00Z");
    private static final long MARGEM_MS = 30_000L;

    private final List<String> publicados = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private ControlEventPublisher publisherAcordandoEm(Instant agora) {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        VotingProperties props = new VotingProperties(
                "br-2026-presidencial", "pepper-de-teste", "votes.cast", "votes.receipts",
                5_000L, "votes.control", "2026-10-04T11:00:00Z", FECHA.toString(), MARGEM_MS);

        ControlEventPublisher publisher = new ControlEventPublisher(
                kafka,
                props,
                new ElectionSchedule(ElectionId.of("br-2026-presidencial"),
                        Instant.parse("2026-10-04T11:00:00Z"), FECHA),
                Clock.fixed(agora, ZoneOffset.UTC));

        publisher.heartbeat();

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafka, org.mockito.Mockito.atLeastOnce())
                .send(anyString(), anyString(), corpo.capture());
        publicados.clear();
        publicados.addAll(corpo.getAllValues());
        return publisher;
    }

    private List<JsonNode> eventosDoTipo(String tipo) throws Exception {
        List<JsonNode> saida = new ArrayList<>();
        for (String json : publicados) {
            JsonNode no = EventJson.mapper().readTree(json);
            if (tipo.equals(no.get("type").asText())) {
                saida.add(no);
            }
        }
        return saida;
    }

    @Test
    void naoPublicaSentinelaAntesDoPrazo() throws Exception {
        publisherAcordandoEm(FECHA.minusSeconds(10));

        assertThat(eventosDoTipo("ELECTION_CLOSED")).isEmpty();
        assertThat(eventosDoTipo("HEARTBEAT")).hasSize(1);
    }

    // O ponto do teste: o agendador acordou 7s atrasado, e a sentinela nao herda esse atraso.
    @Test
    void aSentinelaEDerivadaDoPrazoENaoDoMomentoEmQueOAgendadorAcordou() throws Exception {
        publisherAcordandoEm(FECHA.plusSeconds(7));

        assertThat(eventosDoTipo("ELECTION_CLOSED")).singleElement().satisfies(evento ->
                assertThat(Instant.parse(evento.get("at").asText()))
                        .isEqualTo(FECHA.plusMillis(MARGEM_MS)));
    }

    @Test
    void oMesmoPrazoDaOMesmoCarimboIndependenteDoAtraso() throws Exception {
        publisherAcordandoEm(FECHA.plusSeconds(1));
        Instant primeiro = Instant.parse(eventosDoTipo("ELECTION_CLOSED").get(0).get("at").asText());

        publisherAcordandoEm(FECHA.plusSeconds(29));
        Instant segundo = Instant.parse(eventosDoTipo("ELECTION_CLOSED").get(0).get("at").asText());

        assertThat(primeiro).isEqualTo(segundo);
    }

    /**
     * A margem existe para que a marca d'agua ultrapasse a ultima janela: ela e
     * {@code maior_horario_visto - out_of_orderness}, entao uma sentinela carimbada perto
     * demais do fechamento deixaria a ultima janela sem fechar.
     */
    @Test
    void aSentinelaFicaAlemDoPrazoOBastanteParaAMarcaDaguaPassar() throws Exception {
        publisherAcordandoEm(FECHA.plusSeconds(2));
        Instant sentinela = Instant.parse(eventosDoTipo("ELECTION_CLOSED").get(0).get("at").asText());

        long outOfOrdernessPadraoDoJob = 5_000L;
        assertThat(sentinela).isAfter(FECHA.plusMillis(outOfOrdernessPadraoDoJob));
    }

    @Test
    void publicaASentinelaUmaVezSo() throws Exception {
        ControlEventPublisher publisher = publisherAcordandoEm(FECHA.plusSeconds(1));
        assertThat(eventosDoTipo("ELECTION_CLOSED")).hasSize(1);

        // Segundo tique do agendador: mais batimentos, nenhuma sentinela nova.
        publisher.heartbeat();
        assertThat(eventosDoTipo("ELECTION_CLOSED")).hasSize(1);
    }
}
