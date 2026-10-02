package com.virtualinvestor.persist.projection;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.SequencedEvent;
import java.util.List;

/** Rebuilds a {@link Projection} by folding every event in a log, in order. */
public final class ProjectionBuilder {

    private ProjectionBuilder() {
    }

    public static <T> T rebuild(Projection<T> projection, List<SequencedEvent> events) {
        for (SequencedEvent sequenced : events) {
            projection.apply(sequenced.seq(), sequenced.event());
        }
        return projection.result();
    }

    public static <T> T rebuild(Projection<T> projection, java.util.stream.Stream<GameEvent> events) {
        long[] seq = {0L};
        events.forEach(event -> projection.apply(++seq[0], event));
        return projection.result();
    }
}
