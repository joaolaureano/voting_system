package com.voting.streaming.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.contracts.EventJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

/**
 * Serializa um evento como JSON tambem <em>entre operadores</em> e dentro do estado.
 *
 * <p>Por que nao o serializador automatico do Flink: os contratos sao {@code record}s, que
 * nao satisfazem o contrato de POJO do Flink (construtor vazio e setters). Sem isto o Flink
 * cairia no Kryo, que nao instancia records. Usar o mesmo JSON do topico em todo lugar tem,
 * de quebra, duas vantagens: um unico formato para depurar, e estado que sobrevive a adicao
 * de campos no contrato.
 *
 * <p>O custo e o esperado: JSON e mais lento e mais volumoso que um serializador binario
 * gerado. Se a apuracao crescer a ponto de isso pesar, o caminho e um formato binario
 * (Avro/Protobuf) nos contratos - nao um remendo aqui.
 */
public final class JsonTypeSerializer<T> extends TypeSerializer<T> {

    private static final long serialVersionUID = 1L;

    private final Class<T> type;
    private transient ObjectMapper mapper;

    public JsonTypeSerializer(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = EventJson.create();
        }
        return mapper;
    }

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public TypeSerializer<T> duplicate() {
        return new JsonTypeSerializer<>(type);
    }

    @Override
    public T createInstance() {
        return null;
    }

    @Override
    public T copy(T from) {
        return from;
    }

    @Override
    public T copy(T from, T reuse) {
        return from;
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(T record, DataOutputView target) throws IOException {
        byte[] bytes = mapper().writeValueAsBytes(record);
        target.writeInt(bytes.length);
        target.write(bytes);
    }

    @Override
    public T deserialize(DataInputView source) throws IOException {
        byte[] bytes = new byte[source.readInt()];
        source.readFully(bytes);
        return mapper().readValue(new String(bytes, StandardCharsets.UTF_8), type);
    }

    @Override
    public T deserialize(T reuse, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        byte[] bytes = new byte[source.readInt()];
        source.readFully(bytes);
        target.writeInt(bytes.length);
        target.write(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JsonTypeSerializer<?> other && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public TypeSerializerSnapshot<T> snapshotConfiguration() {
        return new JsonTypeSerializerSnapshot<>(type);
    }

    /** Snapshot do serializador, gravado junto com o savepoint. */
    public static final class JsonTypeSerializerSnapshot<T> implements TypeSerializerSnapshot<T> {

        private static final int VERSION = 1;

        private Class<T> type;

        /** Exigido pelo Flink para reconstruir o snapshot na restauracao. */
        public JsonTypeSerializerSnapshot() {
        }

        JsonTypeSerializerSnapshot(Class<T> type) {
            this.type = type;
        }

        @Override
        public int getCurrentVersion() {
            return VERSION;
        }

        @Override
        public void writeSnapshot(DataOutputView out) throws IOException {
            out.writeUTF(type.getName());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void readSnapshot(int readVersion, DataInputView in, ClassLoader classLoader)
                throws IOException {
            String className = in.readUTF();
            try {
                type = (Class<T>) Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException e) {
                throw new IOException("classe do evento ausente na restauracao: " + className, e);
            }
        }

        @Override
        public TypeSerializer<T> restoreSerializer() {
            return new JsonTypeSerializer<>(type);
        }
    }
}
