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
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/internal/api"
	"github.com/joaolaureano/voting_system/voting-merkle/internal/kafkaio"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
)

func main() {
	brokers := flag.String("brokers", env("KAFKA_BROKERS", "localhost:9092"), "brokers Kafka, separados por virgula")
	acceptedTopic := flag.String("accepted-topic", env("ACCEPTED_TOPIC", "votes.accepted"), "topico dos votos admitidos")
	windowsTopic := flag.String("windows-topic", env("WINDOWS_TOPIC", "votes.windows"), "topico dos marcadores de janela")
	rootsTopic := flag.String("roots-topic", env("ROOTS_TOPIC", "merkle.roots"), "topico das raizes publicadas")
	addr := flag.String("addr", env("HTTP_ADDR", ":8083"), "endereco da API de provas")
	flag.Parse()

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))

	cfg := kafkaio.Config{
		Brokers:       strings.Split(*brokers, ","),
		AcceptedTopic: *acceptedTopic,
		WindowsTopic:  *windowsTopic,
		RootsTopic:    *rootsTopic,
		// Grupo efemero: sem persistencia, a cadeia vive so em memoria e um restart precisa
		// reler tudo desde o inicio. Ver a limitacao conhecida no README.
		ConsumerGroup:  "merkle-service-" + time.Now().UTC().Format("20060102T150405"),
		PublishTimeout: 10 * time.Second,
	}

	log := checkpoint.NewLog()
	publisher := kafkaio.NewPublisher(cfg)
	defer publisher.Close()

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
		"raizes", cfg.RootsTopic, "grupo", cfg.ConsumerGroup)

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

func env(chave, padrao string) string {
	if valor := os.Getenv(chave); valor != "" {
		return valor
	}
	return padrao
}
