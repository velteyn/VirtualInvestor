package com.virtualinvestor.persist.event;

import com.virtualinvestor.core.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Buffers events and flushes them to an {@link EventLog} in batches, so the simulation's hot
 * path does not block on a database round-trip per event. Sequence numbers are assigned here,
 * monotonically, from the log's current high-water mark.
 */
public final class BatchedEventLogWriter {

    private final EventLog log;
    private final int batchSize;
    private final List<SequencedEvent> buffer = new ArrayList<>();

    private long nextSeq;

    public BatchedEventLogWriter(EventLog log, int batchSize) {
        this.log = Objects.requireNonNull(log, "log");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.nextSeq = log.latestSeq() + 1L;
    }

    public void append(GameEvent event) {
        buffer.add(new SequencedEvent(nextSeq++, Objects.requireNonNull(event, "event")));
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    public void appendAll(List<? extends GameEvent> events) {
        for (GameEvent event : events) {
            append(event);
        }
    }

    /** Write any buffered events now. */
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        log.appendBatch(List.copyOf(buffer));
        buffer.clear();
    }

    public int pending() {
        return buffer.size();
    }
}
