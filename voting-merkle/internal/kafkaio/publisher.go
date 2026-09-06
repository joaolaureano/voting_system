package kafkaio

import (
	"context"
	"encoding/json"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/internal/voting"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
	"github.com/segmentio/kafka-go"
)

// Publisher escreve as raizes seladas em merkle.roots.
//
// Chave = windowId, e o topico e compactado: a ultima raiz de cada janela sobrevive
// indefinidamente, entao um auditor que leia do inicio reconstroi a cadeia completa.
type Publisher struct {
	writer  *kafka.Writer
	timeout time.Duration
}

// NewPublisher monta o produtor.
func NewPublisher(cfg Config) *Publisher {
	return &Publisher{
		writer: &kafka.Writer{
			Addr:  kafka.TCP(cfg.Brokers...),
			Topic: cfg.RootsTopic,
			// Uma raiz por janela e um volume baixissimo; agrupar so atrasaria a
			// publicacao sem economizar nada que importe.
			BatchSize:    1,
			BatchTimeout: 10 * time.Millisecond,
			// A raiz e o compromisso publico da apuracao: se o broker nao confirmou em
			// todas as replicas, ela nao foi publicada.
			RequiredAcks: kafka.RequireAll,
			Balancer:     &kafka.Hash{},
		},
		timeout: cfg.PublishTimeout,
	}
}

// Publish envia a raiz de um checkpoint selado.
func (p *Publisher) Publish(c *checkpoint.Checkpoint) error {
	payload, err := json.Marshal(voting.NewRootRecord(c))
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), p.timeout)
	defer cancel()

	return p.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(c.BatchID),
		Value: payload,
	})
}

// Close fecha o produtor.
func (p *Publisher) Close() error { return p.writer.Close() }
