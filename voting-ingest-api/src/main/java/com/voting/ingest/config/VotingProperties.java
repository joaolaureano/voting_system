package com.voting.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da borda.
 *
 * @param electionId    eleicao que esta instancia atende
 * @param receiptPepper segredo do servidor usado na emissao do comprovante. Sem ele, quem
 *                      conhece o identificador do eleitor recalcula o hash para cada
 *                      candidato e descobre o voto. Nunca versionar o valor real.
 * @param votesTopic    topico dos votos aceitos, consumido pela apuracao
 * @param receiptsTopic topico dos comprovantes, consultado pelo eleitor
 * @param sendTimeoutMs quanto esperar pelo ack do Kafka antes de responder erro
 * @param controlTopic  topico dos eventos de controle que empurram a marca d'agua do Flink
 * @param opensAt       inicio da votacao em ISO-8601; vazio significa sem prazo
 * @param closesAt      fim da votacao em ISO-8601; vazio significa sem prazo
 */
@ConfigurationProperties(prefix = "voting")
public record VotingProperties(
        String electionId,
        String receiptPepper,
        String votesTopic,
        String receiptsTopic,
        long sendTimeoutMs,
        String controlTopic,
        String opensAt,
        String closesAt) {

    public VotingProperties {
        if (receiptPepper == null || receiptPepper.isBlank()) {
            throw new IllegalStateException(
                    "voting.receipt-pepper e obrigatorio: sem ele os comprovantes sao adivinhaveis");
        }
        if (votesTopic == null || votesTopic.isBlank()) {
            votesTopic = "votes.cast";
        }
        if (receiptsTopic == null || receiptsTopic.isBlank()) {
            receiptsTopic = "votes.receipts";
        }
        if (sendTimeoutMs <= 0) {
            sendTimeoutMs = 5_000L;
        }
        if (controlTopic == null || controlTopic.isBlank()) {
            controlTopic = "votes.control";
        }
        if (opensAt == null) {
            opensAt = "";
        }
        if (closesAt == null) {
            closesAt = "";
        }
        if (opensAt.isBlank() != closesAt.isBlank()) {
            throw new IllegalStateException(
                    "informe voting.opens-at e voting.closes-at juntos, ou nenhum dos dois");
        }
    }

    /** True quando a eleicao tem periodo definido. */
    public boolean hasSchedule() {
        return !opensAt.isBlank();
    }
}
