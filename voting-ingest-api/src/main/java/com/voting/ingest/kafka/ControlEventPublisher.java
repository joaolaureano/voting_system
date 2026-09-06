package com.voting.ingest.kafka;

import com.voting.contracts.ControlEvent;
import com.voting.domain.election.ElectionSchedule;
import com.voting.ingest.config.VotingProperties;
import java.time.Clock;
import java.time.Duration;
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
    private final Duration closingMargin;
    private final Clock clock;
    private final AtomicBoolean closingSentinelSent = new AtomicBoolean();

    public ControlEventPublisher(
            KafkaTemplate<String, String> kafka,
            VotingProperties properties,
            ElectionSchedule schedule,
            Clock clock) {
        super(kafka, properties.sendTimeoutMs());
        this.topic = properties.controlTopic();
        this.electionId = properties.electionId();
        this.schedule = schedule;
        this.closingMargin = Duration.ofMillis(properties.closingMarginMs());
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
        Instant now = clock.instant();
        publish(ControlEvent.heartbeat(electionId, now));
        publishClosingSentinelOnce(now);
    }

    /**
     * Sentinela de encerramento, uma vez so.
     *
     * <p>O carimbo e {@code closesAt + closingMargin}, e <strong>nao</strong> o instante em que
     * o agendador acordou. Dois motivos, e os dois importam:
     *
     * <p>Determinismo. O agendador dispara em algum ponto dentro do seu intervalo, entao um
     * carimbo de "agora" faria a sentinela ter um horario diferente a cada execucao - e o
     * horario dela entra no log, que e a fonte a partir da qual as raizes sao reproduzidas.
     * Derivando de {@code closesAt}, a sentinela e a mesma independentemente de quando o
     * agendador acordou.
     *
     * <p>Suficiencia. A marca d'agua do Flink e {@code maior_horario_visto - out_of_orderness}.
     * Uma sentinela carimbada poucos segundos apos o fechamento produziria uma marca d'agua
     * <em>anterior</em> ao fechamento, e a ultima janela nao fecharia. A margem tem de exceder
     * o {@code out.of.orderness} do job - ver {@code voting.closing-margin-ms}.
     *
     * <p>Ela nao <em>causa</em> o encerramento: quem recusa votos fora do prazo e a regra de
     * dominio, tanto aqui quanto no Flink. A sentinela so garante que a apuracao seja
     * finalizada e selada.
     */
    private void publishClosingSentinelOnce(Instant now) {
        if (schedule.isUnbounded() || !schedule.hasClosedAt(now)) {
            return;
        }
        if (closingSentinelSent.compareAndSet(false, true)) {
            Instant sentinelAt = schedule.closesAt().plus(closingMargin);
            LOG.info("votacao encerrada em {}; sentinela carimbada em {}",
                    schedule.closesAt(), sentinelAt);
            publish(ControlEvent.electionClosed(electionId, sentinelAt));
        }
    }

    private void publish(ControlEvent event) {
        try {
            send(topic, event.type(), event);
        } catch (RuntimeException e) {
            // Um batimento perdido nao e incidente: o proximo empurra a marca d'agua do mesmo
            // jeito. Derrubar a API por causa disso seria muito pior.
            LOG.warn("evento de controle nao publicado: {}", e.toString());
        }
    }
}
