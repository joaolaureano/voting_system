# Imagem oficial do Flink com o diretorio de checkpoints ja pertencente ao usuario flink.
# Sem isto o volume nomeado nasce como root e o JobManager falha ao criar o checkpoint.
FROM flink:1.20.1-java17
RUN mkdir -p /flink-checkpoints && chown -R flink:flink /flink-checkpoints
