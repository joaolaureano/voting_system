package com.voting.ingest.kafka;

import com.voting.contracts.ControlEvent;
import com.voting.domain.election.ElectionSchedule;
import com.voting.ingest.config.VotingProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica o batimento que mantem o tempo de evento andando, e a sentinela de encerramento.
 *
 * <p>Vive na borda porque e aqui que mora o relogio do servidor - o mesmo que carimba os
 * votos. Um batimento com horario de outra fonte poderia atrasar ou adiantar a marca d'agua
 * em relacao aos votos.
 *
 * <p>Varias instancias da API publicam batimentos em paralelo, e nao ha problema nisso: o
 * Flink so olha o maior horario visto. Duplicatas nao contam nada, so empurram o tempo.
 */
@Component
public class ControlEventPublisher extends KafkaEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(ControlEventPublisher.class);

    private final String topic;
    private final String electionId;
    private final ElectionSchedule schedule;
    private final Clock clock;
    private final AtomicBoolean encerramentoPublicado = new AtomicBoolean();

    public ControlEventPublisher(
            KafkaTemplate<String, String> kafka,
            VotingProperties properties,
            ElectionSchedule schedule,
            Clock clock) {
        super(kafka, properties.sendTimeoutMs());
        this.topic = properties.controlTopic();
        this.electionId = properties.electionId();
        this.schedule = schedule;
        this.clock = clock;
    }

    /**
     * Batimento periodico.
     *
     * <p>O intervalo precisa ser confortavelmente menor que a janela da Merkle Tree: e o
     * batimento que faz uma janela sem votos fechar, e uma janela que nao fecha e um grupo de
     * eleitores sem prova de inclusao.
     */
    @Scheduled(fixedDelayString = "${voting.heartbeat-interval-ms:5000}")
    public void heartbeat() {
        Instant agora = clock.instant();
        publicar(ControlEvent.heartbeat(electionId, agora));
        publicarEncerramentoSeChegouAHora(agora);
    }

    /**
     * Sentinela de encerramento, uma vez so.
     *
     * <p>Carimbada depois do fechamento o bastante para que a marca d'agua ultrapasse a ultima
     * janela e ela feche - do contrario os ultimos votos da eleicao nunca virariam raiz.
     *
     * <p>Ela nao <em>causa</em> o encerramento: quem recusa votos fora do prazo e a regra de
     * dominio, tanto aqui quanto no Flink. A sentinela so garante que a apuracao seja
     * finalizada e selada.
     */
    private void publicarEncerramentoSeChegouAHora(Instant agora) {
        if (schedule.isUnbounded() || !schedule.hasClosedAt(agora)) {
            return;
        }
        if (encerramentoPublicado.compareAndSet(false, true)) {
            LOG.info("votacao encerrada em {}; publicando sentinela", schedule.closesAt());
            publicar(ControlEvent.electionClosed(electionId, agora));
        }
    }

    private void publicar(ControlEvent evento) {
        try {
            send(topic, evento.type(), evento);
        } catch (RuntimeException e) {
            // Um batimento perdido nao e incidente: o proximo empurra a marca d'agua do mesmo
            // jeito. Derrubar a API por causa disso seria muito pior.
            LOG.warn("batimento nao publicado: {}", e.toString());
        }
    }
}
