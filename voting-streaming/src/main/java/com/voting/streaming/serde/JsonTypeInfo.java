package com.voting.streaming.serde;

import com.voting.contracts.AcceptedVoteEvent;
import com.voting.contracts.ControlEvent;
import com.voting.contracts.RejectedVoteEvent;
import com.voting.contracts.TallyUpdateEvent;
import com.voting.contracts.VoteCastEvent;
import com.voting.contracts.WindowMarkerEvent;
import java.util.Objects;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;

/**
 * {@link TypeInformation} dos eventos, apoiada em {@link JsonTypeSerializer}.
 *
 * <p>Declara explicitamente o tipo de cada fluxo do job, no lugar da inferencia automatica,
 * que nao funciona para {@code record}s.
 */
public final class JsonTypeInfo<T> extends TypeInformation<T> {

    private static final long serialVersionUID = 1L;

    public static final TypeInformation<VoteCastEvent> VOTE_CAST = jsonOf(VoteCastEvent.class);
    public static final TypeInformation<RejectedVoteEvent> REJECTED = jsonOf(RejectedVoteEvent.class);
    public static final TypeInformation<TallyUpdateEvent> TALLY = jsonOf(TallyUpdateEvent.class);
    public static final TypeInformation<AcceptedVoteEvent> ACCEPTED = jsonOf(AcceptedVoteEvent.class);
    public static final TypeInformation<WindowMarkerEvent> WINDOW_MARKER = jsonOf(WindowMarkerEvent.class);
    public static final TypeInformation<ControlEvent> CONTROL = jsonOf(ControlEvent.class);
    public static final TypeInformation<com.voting.streaming.merkle.TimelineEvent> TIMELINE =
            jsonOf(com.voting.streaming.merkle.TimelineEvent.class);

    private final Class<T> type;

    private JsonTypeInfo(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /** Nome proprio, e nao {@code of}: {@code TypeInformation.of} ja existe e faz outra coisa. */
    private static <T> TypeInformation<T> jsonOf(Class<T> type) {
        return new JsonTypeInfo<>(type);
    }

    @Override
    public boolean isBasicType() {
        return false;
    }

    @Override
    public boolean isTupleType() {
        return false;
    }

    @Override
    public int getArity() {
        return 1;
    }

    @Override
    public int getTotalFields() {
        return 1;
    }

    @Override
    public Class<T> getTypeClass() {
        return type;
    }

    @Override
    public boolean isKeyType() {
        // As chaves do job sao Strings extraidas pelo dominio; o evento inteiro nunca e chave.
        return false;
    }

    @Override
    public TypeSerializer<T> createSerializer(ExecutionConfig config) {
        return new JsonTypeSerializer<>(type);
    }

    @Override
    public String toString() {
        return "Json<" + type.getSimpleName() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JsonTypeInfo<?> other && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean canEqual(Object obj) {
        return obj instanceof JsonTypeInfo;
    }
}
