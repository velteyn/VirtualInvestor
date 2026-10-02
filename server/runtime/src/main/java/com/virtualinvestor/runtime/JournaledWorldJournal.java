package com.virtualinvestor.runtime;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.session.RoundRecoveryService;
import java.util.List;
import java.util.Objects;

/**
 * Journal backed by the durable event log (design D5). Events are appended through the batched
 * writer so the tick is never blocked on a database round-trip, and a checkpoint is written on
 * a cadence of ticks so recovery never replays from the start.
 */
public final class JournaledWorldJournal implements WorldJournal {

    private final BatchedEventLogWriter writer;
    private final RoundRecoveryService recovery;
    private final long checkpointEveryTicks;

    private long lastCheckpointTick;

    public JournaledWorldJournal(
            BatchedEventLogWriter writer, RoundRecoveryService recovery, long checkpointEveryTicks) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        if (checkpointEveryTicks <= 0L) {
            throw new IllegalArgumentException("checkpointEveryTicks must be positive");
        }
        this.checkpointEveryTicks = checkpointEveryTicks;
    }

    @Override
    public void append(List<GameEvent> events) {
        writer.appendAll(events);
    }

    @Override
    public void checkpoint(long tick) {
        if (tick - lastCheckpointTick < checkpointEveryTicks) {
            return;
        }
        lastCheckpointTick = tick;
        recovery.checkpoint(tick);
    }

    /** Flush any buffered events, e.g. before shutdown. */
    public void flush() {
        writer.flush();
    }
}
