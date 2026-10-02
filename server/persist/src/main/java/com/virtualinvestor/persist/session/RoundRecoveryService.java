package com.virtualinvestor.persist.session;

import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.EventLog;
import com.virtualinvestor.persist.snapshot.Recovery;
import com.virtualinvestor.persist.snapshot.SnapshotStore;
import java.util.List;
import java.util.Objects;

/**
 * Recovers and control a round across process restarts (task 9.6). Recovery rebuilds world
 * state from the latest snapshot plus later events and resumes the clock at the recovered tick.
 * Pause and resume append {@code RoundPaused}/{@code RoundResumed} events and write a snapshot,
 * so a paused round restarts still paused, exactly where it stopped.
 */
public final class RoundRecoveryService {

    /** What recovery produced: the rebuilt event history and the tick to resume from. */
    public record RecoveryResult(List<GameEvent> history, long recoveredTick, RoundState state) {
        public RecoveryResult {
            history = List.copyOf(history);
        }
    }

    private final EventLog log;
    private final SnapshotStore snapshots;
    private final BatchedEventLogWriter writer;

    public RoundRecoveryService(EventLog log, SnapshotStore snapshots, BatchedEventLogWriter writer) {
        this.log = Objects.requireNonNull(log, "log");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public BatchedEventLogWriter writer() {
        return writer;
    }

    /** Rebuild the world and determine whether it was running or paused. */
    public RecoveryResult recover() {
        List<GameEvent> history = Recovery.recover(log, snapshots);
        long tick = history.isEmpty() ? 0L : history.get(history.size() - 1).tick();
        RoundState state = RoundState.CREATED;
        for (GameEvent event : history) {
            if (event instanceof GameEvent.SessionOpened) {
                state = RoundState.RUNNING;
            } else if (event instanceof GameEvent.RoundPaused) {
                state = RoundState.PAUSED;
            } else if (event instanceof GameEvent.RoundResumed) {
                state = RoundState.RUNNING;
            } else if (event instanceof GameEvent.RoundSettled) {
                state = RoundState.SETTLED;
            }
        }
        return new RecoveryResult(history, tick, state);
    }

    /** Append a pause event and checkpoint, so a restart resumes paused. */
    public void pause(String roundId, long tick) {
        writer.append(new GameEvent.RoundPaused(roundId, tick));
        checkpoint(tick);
    }

    /** Append a resume event and checkpoint. */
    public void resume(String roundId, long tick) {
        writer.append(new GameEvent.RoundResumed(roundId, tick));
        checkpoint(tick);
    }

    /** Write a snapshot of the current history at the current sequence, then flush the log. */
    public void checkpoint(long tick) {
        writer.flush();
        snapshots.save(new com.virtualinvestor.persist.snapshot.WorldSnapshot(
                tick, log.latestSeq(), encode(Recovery.recover(log, snapshots))));
    }

    private static String encode(List<GameEvent> events) {
        return com.virtualinvestor.persist.snapshot.WorldStateCodec.encode(events);
    }
}
