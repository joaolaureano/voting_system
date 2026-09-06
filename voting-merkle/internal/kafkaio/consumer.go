// Package kafkaio liga a cadeia de checkpoints aos topicos do Kafka.
//
// E o unico pacote que sabe que o transporte e Kafka. Trocar por outra fila significa
// reescrever este pacote e mais nada.
package kafkaio

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/internal/voting"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
	"github.com/segmentio/kafka-go"
)

// Config aponta o servico para os topicos.
type Config struct {
	Brokers        []string
	AcceptedTopic  string
	WindowsTopic   string
	RootsTopic     string
	ConsumerGroup  string
	PublishTimeout time.Duration
}

// Consumer alimenta o log de checkpoints com o que chega dos dois topicos.
type Consumer struct {
	log       *checkpoint.Log
	publisher *Publisher
	accepted  *kafka.Reader
	windows   *kafka.Reader
	logger    *slog.Logger
}

// NewConsumer monta os leitores.
//
// Os dois topicos comecam sempre do inicio, e o grupo e efemero (um por processo): sem
// persistencia, a cadeia so existe em memoria, entao um restart precisa reconstrui-la
// inteira. Retomar de um offset salvo daria uma arvore com buracos - pior que reconstruir.
func NewConsumer(cfg Config, log *checkpoint.Log, publisher *Publisher, logger *slog.Logger) *Consumer {
	leitor := func(topic string) *kafka.Reader {
		return kafka.NewReader(kafka.ReaderConfig{
			Brokers:     cfg.Brokers,
			Topic:       topic,
			GroupID:     cfg.ConsumerGroup,
			StartOffset: kafka.FirstOffset,
			MinBytes:    1,
			MaxBytes:    10e6,
			MaxWait:     250 * time.Millisecond,
		})
	}
	return &Consumer{
		log:       log,
		publisher: publisher,
		accepted:  leitor(cfg.AcceptedTopic),
		windows:   leitor(cfg.WindowsTopic),
		logger:    logger,
	}
}

// Run consome os dois topicos ate o contexto ser cancelado.
func (c *Consumer) Run(ctx context.Context) error {
	erros := make(chan error, 2)

	go func() { erros <- c.consumir(ctx, c.accepted, c.aoReceberVoto) }()
	go func() { erros <- c.consumir(ctx, c.windows, c.aoReceberMarcador) }()

	// Basta um dos dois cair: sem folhas nao ha o que selar, sem marcadores nada sela.
	err := <-erros
	<-erros
	return err
}

// Close libera os leitores.
func (c *Consumer) Close() error {
	if err := c.accepted.Close(); err != nil {
		return err
	}
	return c.windows.Close()
}

func (c *Consumer) consumir(ctx context.Context, reader *kafka.Reader, trata func([]byte) error) error {
	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("lendo de %s: %w", reader.Config().Topic, err)
		}
		if err := trata(msg.Value); err != nil {
			// Uma mensagem ruim nao pode derrubar a apuracao inteira; fica no log e o
			// contador de folhas do marcador denuncia a lacuna quando a janela nao fechar.
			c.logger.Error("mensagem descartada",
				"topico", msg.Topic, "offset", msg.Offset, "erro", err)
		}
	}
}

func (c *Consumer) aoReceberVoto(payload []byte) error {
	var voto voting.AcceptedVote
	if err := json.Unmarshal(payload, &voto); err != nil {
		return fmt.Errorf("voto aceito ilegivel: %w", err)
	}
	folha, err := voto.Leaf()
	if err != nil {
		return err
	}
	selados, err := c.log.Add(voto.WindowID, folha)
	if err != nil {
		return err
	}
	return c.publicar(selados)
}

func (c *Consumer) aoReceberMarcador(payload []byte) error {
	var marcador voting.WindowMarker
	if err := json.Unmarshal(payload, &marcador); err != nil {
		return fmt.Errorf("marcador ilegivel: %w", err)
	}
	if err := marcador.Validate(); err != nil {
		return err
	}
	selados, err := c.log.Expect(marcador.WindowID, int(marcador.Count))
	if err != nil {
		return err
	}
	return c.publicar(selados)
}

func (c *Consumer) publicar(selados []*checkpoint.Checkpoint) error {
	for _, ponto := range selados {
		c.logger.Info("janela selada",
			"janela", ponto.BatchID,
			"sequencia", ponto.Sequence,
			"folhas", ponto.Size,
			"raiz", fmt.Sprintf("%x", ponto.Root[:8]))

		if err := c.publisher.Publish(ponto); err != nil {
			return fmt.Errorf("publicando raiz de %s: %w", ponto.BatchID, err)
		}
	}
	return nil
}
