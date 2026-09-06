FROM golang:1.24 AS build
WORKDIR /build

# Dependencias primeiro: enquanto go.mod/go.sum nao mudarem, esta camada fica em cache.
COPY go.mod go.sum ./
RUN go mod download

COPY cmd cmd
COPY internal internal
COPY pkg pkg
RUN go test ./... && CGO_ENABLED=0 go build -trimpath -o /merkle-service ./cmd/merkle-service

# O diretorio do diario nasce aqui, com o dono certo. A imagem final e distroless e nao roda
# comando nenhum, e um volume nomeado vazio herda dono e permissao do caminho na imagem - sem
# isso ele nasceria como root e o processo (uid 10001) nao conseguiria abrir o diario.
RUN mkdir -p /data && chown 10001:10001 /data

# Binario estatico: nao precisa de libc, entao a imagem final nao precisa de distribuicao.
FROM gcr.io/distroless/static-debian12
COPY --from=build /merkle-service /merkle-service
COPY --from=build --chown=10001:10001 /data /var/lib/merkle
USER 10001:10001
VOLUME ["/var/lib/merkle"]
EXPOSE 8083
ENTRYPOINT ["/merkle-service"]
