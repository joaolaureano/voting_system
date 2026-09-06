// Package kafkaio liga a cadeia de checkpoints aos topicos do Kafka.
//
// E o unico pacote que sabe que o transporte e Kafka. Trocar por outra fila significa
// reescrever este pacote e mais nada.
package kafkaio

import (
	"context"
	"encoding/json"
	"errors"
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

	// CommitEvery e CommitInterval regulam de quanto em quanto o consumo e confirmado.
	// Cada confirmacao custa um fsync do diario, entao confirmar por mensagem tornaria
	// cada voto uma ida ao disco; esperar demais faz um restart reprocessar mais. Zero
	// em qualquer um dos dois cai no padrao.
	CommitEvery    int
	CommitInterval time.Duration
}

const (
	padraoCommitEvery    = 256
	padraoCommitInterval = 2 * time.Second
	// esperaPorFetch limita o bloqueio de uma busca sem mensagem, para que o silencio no
	// topico nao adie indefinidamente a confirmacao do que ja foi processado.
	esperaPorFetch = 500 * time.Millisecond
)

// Consumer alimenta o log de checkpoints com o que chega dos dois topicos.
type Consumer struct {
	log       *checkpoint.Log
	publisher *Publisher
	accepted  *kafka.Reader
	windows   *kafka.Reader
	logger    *slog.Logger

	commitEvery    int
	commitInterval time.Duration
}

// NewConsumer monta os leitores.
//
// O grupo e duravel e o offset e confirmado a mao, nunca em segundo plano: quem confirma e
// quem acabou de ver o diario chegar ao disco. Enquanto essa ordem valer, um offset
// confirmado significa "estas folhas estao gravadas", e retomar dele nao abre buraco na
// arvore. Com commit automatico por intervalo, a confirmacao poderia passar na frente do
// fsync e o buraco viraria silencioso - que e o pior desfecho possivel aqui.
//
// StartOffset so vale quando o grupo nao tem offset nenhum: primeira subida, ou volume
// novo (ver como main deriva o nome do grupo). Nesse caso, ler do inicio e o certo.
func NewConsumer(cfg Config, log *checkpoint.Log, publisher *Publisher, logger *slog.Logger) *Consumer {
	commitEvery := cfg.CommitEvery
	if commitEvery <= 0 {
		commitEvery = padraoCommitEvery
	}
	commitInterval := cfg.CommitInterval
	if commitInterval <= 0 {
		commitInterval = padraoCommitInterval
	}

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
		log:            log,
		publisher:      publisher,
		accepted:       leitor(cfg.AcceptedTopic),
		windows:        leitor(cfg.WindowsTopic),
		logger:         logger,
		commitEvery:    commitEvery,
		commitInterval: commitInterval,
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
	var pendente kafka.Message
	temPendente := false
	desdeUltima := time.Now()
	processadas := 0

	// confirmar so avanca o offset depois que o diario esta no disco. A ordem entre as duas
	// linhas e a garantia inteira: invertidas, uma queda no meio deixaria o Kafka achando
	// que aquelas folhas ja foram tratadas, e a janela seria selada sem elas.
	confirmar := func() error {
		if !temPendente {
			return nil
		}
		if err := c.log.Sync(); err != nil {
			return fmt.Errorf("gravando o diario antes de confirmar %s: %w", reader.Config().Topic, err)
		}
		// Sem heranca do cancelamento: no desligamento, o trabalho ja duravel merece ser
		// confirmado, senao todo restart reprocessaria o ultimo lote a toa.
		if err := reader.CommitMessages(context.WithoutCancel(ctx), pendente); err != nil {
			return fmt.Errorf("confirmando %s: %w", reader.Config().Topic, err)
		}
		temPendente = false
		processadas = 0
		desdeUltima = time.Now()
		return nil
	}

	for {
		if ctx.Err() != nil {
			return confirmar()
		}

		busca, cancelar := context.WithTimeout(ctx, esperaPorFetch)
		msg, err := reader.FetchMessage(busca)
		cancelar()

		if err != nil {
			if ctx.Err() != nil {
				return confirmar()
			}
			if errors.Is(err, context.DeadlineExceeded) {
				// Topico em silencio: nada a fazer alem de nao deixar o que ja foi
				// processado esperando por uma proxima mensagem que pode nao vir.
				if err := confirmar(); err != nil {
					return err
				}
				continue
			}
			return fmt.Errorf("lendo de %s: %w", reader.Config().Topic, err)
		}

		if err := trata(msg.Value); err != nil {
			// Uma mensagem ruim nao pode derrubar a apuracao inteira; fica no log e o
			// contador de folhas do marcador denuncia a lacuna quando a janela nao fechar.
			// O offset avanca do mesmo jeito: reler uma mensagem ilegivel so repetiria o
			// erro para sempre.
			c.logger.Error("mensagem descartada",
				"topico", msg.Topic, "offset", msg.Offset, "erro", err)
		}

		pendente, temPendente = msg, true
		processadas++
		if processadas >= c.commitEvery || time.Since(desdeUltima) >= c.commitInterval {
			if err := confirmar(); err != nil {
				return err
			}
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
	if len(selados) == 0 {
		return nil
	}
	// A raiz e um compromisso publico: nao se anuncia o que ainda pode sumir num restart.
	// Sao poucos fsyncs (um por janela), e eles vem antes do anuncio, nao depois.
	if err := c.log.Sync(); err != nil {
		return fmt.Errorf("gravando o diario antes de publicar: %w", err)
	}

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
