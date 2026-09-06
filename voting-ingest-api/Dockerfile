# Build no proprio container para nao depender do JDK da maquina.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Os poms primeiro: enquanto as dependencias nao mudarem, esta camada fica em cache.
COPY pom.xml .
COPY voting-domain/pom.xml voting-domain/
COPY voting-contracts/pom.xml voting-contracts/
COPY voting-application/pom.xml voting-application/
COPY voting-ingest-api/pom.xml voting-ingest-api/
COPY voting-streaming/pom.xml voting-streaming/
COPY voting-benchmark/pom.xml voting-benchmark/
RUN mvn -q -pl voting-ingest-api -am dependency:go-offline -DskipTests || true

COPY voting-domain/src voting-domain/src
COPY voting-contracts/src voting-contracts/src
COPY voting-application/src voting-application/src
COPY voting-ingest-api/src voting-ingest-api/src
RUN mvn -q -pl voting-ingest-api -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 voting
COPY --from=build /build/voting-ingest-api/target/voting-ingest-api-*.jar app.jar
USER voting
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
