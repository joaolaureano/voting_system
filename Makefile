# Atalhos do ciclo de desenvolvimento. Nenhuma logica mora aqui: e so o roteiro de operacao.

JAVA_HOME ?= /opt/homebrew/opt/openjdk@21
MVN       := JAVA_HOME=$(JAVA_HOME) mvn
COMPOSE   := docker compose -f infra/docker-compose.yml
KAFKA     := $(COMPOSE) exec -T kafka /opt/kafka/bin
# Janela curta no ambiente local: as raizes aparecem em segundos, e nao a cada minuto.
JOB_ARGS  ?= --bootstrap.servers kafka:9092 --merkle.window.ms 15000
# Perfil do benchmark. Ex.: make bench BENCH="-Dvotes=50000 -Dramp=60 -DduplicateRate=0.05"
BENCH     ?= -Dvotes=10000 -Dramp=30 -DduplicateRate=0.1

.PHONY: help test test-go build up down submit cancel bench bench-data results rejected roots proof chain restart-merkle logs clean

help:
	@grep -E '^[a-z-]+:.*?## .*$$' $(MAKEFILE_LIST) | sed 's/:.*## /\t/'

test: test-go ## Roda todos os testes (Java e Go)
	$(MVN) test

test-go: ## Roda os testes do servico Merkle
	cd voting-merkle && go test ./...

build: ## Compila tudo e gera o fat-jar do job Flink
	$(MVN) -DskipTests package

up: build ## Sobe Kafka, Flink, a API de ingestao e o kafka-ui
	$(COMPOSE) up -d --build
	@echo "API      http://localhost:8081"
	@echo "Flink    http://localhost:8082"
	@echo "Merkle   http://localhost:8083"
	@echo "Kafka UI http://localhost:8080"

submit: ## Submete o job de apuracao ao cluster Flink
	$(COMPOSE) exec jobmanager flink run -d /opt/flink/usrjob/voting-streaming.jar $(JOB_ARGS)

cancel: ## Cancela o job em execucao
	@$(COMPOSE) exec jobmanager sh -c 'flink list -r | grep apuracao-de-votos | sed "s/.*: \([0-9a-f]*\) :.*/\1/" | xargs -r -n1 flink cancel'

bench: ## Teste de carga com Gatling: make bench BENCH="-Dvotes=50000 -Dramp=60"
	$(MVN) -q -pl voting-benchmark gatling:test $(BENCH)
	@echo "relatorio: voting-benchmark/target/gatling/*/index.html"

bench-data: ## Regera candidatos.csv a partir de partidos.csv (semente fixa)
	$(MVN) -q -pl voting-benchmark exec:java \
		-Dexec.mainClass=com.voting.benchmark.dataset.CandidatosGenerator

results: ## Mostra a apuracao corrente por candidato e por estado
	@echo "== por candidato"; $(KAFKA)/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
		--topic results.by-candidate --from-beginning --property print.key=true --timeout-ms 4000 2>/dev/null | tail -20
	@echo "== por estado"; $(KAFKA)/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
		--topic results.by-state --from-beginning --property print.key=true --timeout-ms 4000 2>/dev/null | tail -20

roots: ## Mostra a cadeia de raizes ja seladas
	@curl -s localhost:8083/roots | python3 -m json.tool

proof: ## Prova de inclusao de um recibo: make proof RECEIPT=<hash>
	@curl -s localhost:8083/proof/$(RECEIPT) | python3 -m json.tool

chain: ## Estado da cadeia: identidade do diario, cabeca e janelas abertas
	@curl -s localhost:8083/healthz | python3 -m json.tool

restart-merkle: ## Reinicia so o servico Merkle: a cadeia tem de voltar do disco intacta
	@$(COMPOSE) restart merkle
	@sleep 3
	@$(COMPOSE) logs --tail 3 merkle

rejected: ## Mostra os votos recusados pela apuracao
	@$(KAFKA)/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
		--topic votes.rejected --from-beginning --timeout-ms 4000 2>/dev/null | tail -20

logs: ## Segue os logs do cluster
	$(COMPOSE) logs -f --tail 50

down: ## Derruba tudo e apaga os dados (topicos, checkpoints do Flink e o diario da cadeia)
	$(COMPOSE) down -v

clean: ## Limpa os artefatos de build
	$(MVN) -q clean
