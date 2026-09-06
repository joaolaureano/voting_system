// Command merkle-service sela a apuracao em arvores de Merkle e serve provas de inclusao.
//
// Consome votes.accepted (as folhas, ja depois do dedup) e votes.windows (o sinal de que uma
// janela fechou), sela cada janela numa arvore RFC 6962 encadeada com a anterior, publica a
// raiz em merkle.roots e responde ao eleitor em GET /proof/{recibo}.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/internal/api"
	"github.com/joaolaureano/voting_system/voting-merkle/internal/kafkaio"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/journal"
)

func main() {
	brokers := flag.String("brokers", env("KAFKA_BROKERS", "localhost:9092"), "brokers Kafka, separados por virgula")
	acceptedTopic := flag.String("accepted-topic", env("ACCEPTED_TOPIC", "votes.accepted"), "topico dos votos admitidos")
	windowsTopic := flag.String("windows-topic", env("WINDOWS_TOPIC", "votes.windows"), "topico dos marcadores de janela")
	rootsTopic := flag.String("roots-topic", env("ROOTS_TOPIC", "merkle.roots"), "topico das raizes publicadas")
	addr := flag.String("addr", env("HTTP_ADDR", ":8083"), "endereco da API de provas")
	dataDir := flag.String("data-dir", env("DATA_DIR", "/var/lib/merkle"),
		"diretorio do diario da cadeia; vazio desliga a persistencia")
	flag.Parse()

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))

	log, grupo, fechar, pendentes, err := abrirCadeia(*dataDir, logger)
	if err != nil {
		logger.Error("cadeia indisponivel", "erro", err)
		os.Exit(1)
	}
	defer fechar()

	cfg := kafkaio.Config{
		Brokers:        strings.Split(*brokers, ","),
		AcceptedTopic:  *acceptedTopic,
		WindowsTopic:   *windowsTopic,
		RootsTopic:     *rootsTopic,
		ConsumerGroup:  grupo,
		PublishTimeout: 10 * time.Second,
	}

	publisher := kafkaio.NewPublisher(cfg)
	defer publisher.Close()

	// Selos que a retomada fechou (queda entre gravar o marcador e gravar o selo) ainda nao
	// foram anunciados em merkle.roots. Os demais ja estavam la desde a execucao anterior.
	for _, ponto := range pendentes {
		if err := publisher.Publish(ponto); err != nil {
			logger.Error("publicando raiz recuperada", "janela", ponto.BatchID, "erro", err)
			os.Exit(1)
		}
		logger.Info("raiz recuperada publicada", "janela", ponto.BatchID, "sequencia", ponto.Sequence)
	}

	consumer := kafkaio.NewConsumer(cfg, log, publisher, logger)
	defer consumer.Close()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	servidor := &http.Server{
		Addr:              *addr,
		Handler:           api.NewServer(log).Handler(),
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		logger.Info("servindo provas", "addr", *addr)
		if err := servidor.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("servidor HTTP caiu", "erro", err)
			stop()
		}
	}()

	logger.Info("consumindo",
		"brokers", cfg.Brokers, "folhas", cfg.AcceptedTopic, "janelas", cfg.WindowsTopic,
		"raizes", cfg.RootsTopic, "grupo", cfg.ConsumerGroup,
		"checkpoints", len(log.Checkpoints()), "janelasAbertas", log.Pending())

	erroConsumo := make(chan error, 1)
	go func() { erroConsumo <- consumer.Run(ctx) }()

	select {
	case <-ctx.Done():
		logger.Info("encerrando")
	case err := <-erroConsumo:
		if err != nil {
			logger.Error("consumo interrompido", "erro", err)
		}
	}

	desligar, cancelar := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancelar()
	if err := servidor.Shutdown(desligar); err != nil {
		logger.Error("shutdown do HTTP", "erro", err)
	}
}

// abrirCadeia decide entre a cadeia persistente e a cadeia so em memoria, e devolve o nome
// do grupo de consumo que combina com essa escolha.
//
// O nome do grupo carrega a identidade do diario, e isso e o cerne da retomada. Enquanto o
// volume sobreviver, o grupo e o mesmo e o consumo continua do offset confirmado. Se o
// volume for perdido, o diario nasce com identidade nova, o grupo tambem e novo, e a leitura
// recomeca do inicio dos topicos - que e exatamente o certo, porque nao ha estado local a
// que aquele offset antigo correspondesse. Um grupo fixo faria o servico retomar de um
// offset sem ter as folhas anteriores: uma arvore com buracos, selada em silencio.
func abrirCadeia(dataDir string, logger *slog.Logger) (*checkpoint.Log, string, func(), []*checkpoint.Checkpoint, error) {
	if dataDir == "" {
		// Escape hatch de desenvolvimento: sem disco, a cadeia vive so em memoria e cada
		// subida precisa reler os topicos inteiros, entao o grupo tem de ser descartavel.
		logger.Warn("persistencia desligada: a cadeia vive so em memoria")
		grupo := "merkle-service-efemero-" + time.Now().UTC().Format("20060102T150405")
		return checkpoint.NewLog(), grupo, func() {}, nil, nil
	}

	caminho := filepath.Join(dataDir, "chain.wal")
	diario, err := journal.Open(caminho)
	if err != nil {
		return nil, "", nil, nil, err
	}

	novo := diario.Empty()
	inicio := time.Now()
	log, pendentes, err := checkpoint.NewLogWithStore(diario)
	if err != nil {
		diario.Close()
		return nil, "", nil, nil, fmt.Errorf("retomando %s: %w", caminho, err)
	}

	logger.Info("diario aberto",
		"caminho", caminho, "id", diario.ID(), "novo", novo, "bytes", diario.Size(),
		"checkpoints", len(log.Checkpoints()), "selosRecuperados", len(pendentes),
		"retomadaMs", time.Since(inicio).Milliseconds())

	fechar := func() {
		if err := diario.Close(); err != nil {
			logger.Error("fechando o diario", "erro", err)
		}
	}
	return log, "merkle-service-" + diario.ID(), fechar, pendentes, nil
}

func env(chave, padrao string) string {
	if valor := os.Getenv(chave); valor != "" {
		return valor
	}
	return padrao
}
