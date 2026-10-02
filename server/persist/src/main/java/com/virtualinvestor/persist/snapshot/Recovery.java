package com.virtualinvestor.persist.snapshot;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.EventLog;
import com.virtualinvestor.persist.event.SequencedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rebuilds a round's world state from its latest snapshot plus the events after it. If no
 * snapshot exists, the whole log is replayed (the snapshot's sequence is 0).
 */
public final class Recovery {

    private Recovery() {
    }

    public static List<GameEvent> recover(EventLog log, SnapshotStore snapshots) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(snapshots, "snapshots");
        long afterSeq = 0L;
        List<GameEvent> state = new ArrayList<>();
        WorldSnapshot snapshot = snapshots.latest().orElse(null);
        if (snapshot != null) {
            state.addAll(WorldStateCodec.decode(snapshot.state()));
            afterSeq = snapshot.seq();
        }
        for (SequencedEvent sequenced : log.readFrom(afterSeq + 1L)) {
            state.add(sequenced.event());
        }
        return List.copyOf(state);
    }
}
