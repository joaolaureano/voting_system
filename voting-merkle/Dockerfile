FROM golang:1.24 AS build
WORKDIR /build

# Dependencias primeiro: enquanto go.mod/go.sum nao mudarem, esta camada fica em cache.
COPY go.mod go.sum ./
RUN go mod download

COPY cmd cmd
COPY internal internal
COPY pkg pkg
RUN go test ./... && CGO_ENABLED=0 go build -trimpath -o /merkle-service ./cmd/merkle-service

# Binario estatico: nao precisa de libc, entao a imagem final nao precisa de distribuicao.
FROM gcr.io/distroless/static-debian12
COPY --from=build /merkle-service /merkle-service
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["/merkle-service"]
