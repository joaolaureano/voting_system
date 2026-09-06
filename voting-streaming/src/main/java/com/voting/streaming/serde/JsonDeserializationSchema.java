package com.voting.streaming.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.contracts.EventJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Le eventos JSON do Kafka.
 *
 * <p>Uma mensagem ilegivel e registrada e descartada, em vez de derrubar o job. A alternativa
 * - falhar - transformaria uma unica mensagem malformada num loop de restart que paralisa a
 * apuracao inteira. O descarte fica visivel no log e no contador {@code corruptRecords};
 * quando houver uma fila de mensagens mortas, e para la que elas devem ir.
 */
public final class JsonDeserializationSchema<T> implements DeserializationSchema<T> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(JsonDeserializationSchema.class);

    private final Class<T> type;
    private final TypeInformation<T> typeInfo;
    private transient ObjectMapper mapper;
    private transient org.apache.flink.metrics.Counter corruptRecords;

    public JsonDeserializationSchema(Class<T> type, TypeInformation<T> typeInfo) {
        this.type = Objects.requireNonNull(type, "type");
        this.typeInfo = Objects.requireNonNull(typeInfo, "typeInfo");
    }

    @Override
    public void open(InitializationContext context) {
        mapper = EventJson.create();
        corruptRecords = context.getMetricGroup().counter("corruptRecords");
    }

    @Override
    public T deserialize(byte[] message) throws IOException {
        return mapper().readValue(new String(message, StandardCharsets.UTF_8), type);
    }

    @Override
    public void deserialize(byte[] message, Collector<T> out) {
        try {
            out.collect(deserialize(message));
        } catch (Exception e) {
            if (corruptRecords != null) {
                corruptRecords.inc();
            }
            LOG.warn("mensagem descartada por nao ser um {} valido: {}", type.getSimpleName(), e.toString());
        }
    }

    @Override
    public boolean isEndOfStream(T nextElement) {
        return false;
    }

    @Override
    public TypeInformation<T> getProducedType() {
        return typeInfo;
    }

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = EventJson.create();
        }
        return mapper;
    }
}
