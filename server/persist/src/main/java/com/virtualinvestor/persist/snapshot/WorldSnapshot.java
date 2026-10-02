package com.virtualinvestor.persist.snapshot;

import java.util.Objects;

/**
 * A captured world state at a point in the log. {@code seq} is the sequence number of the last
 * event included in this snapshot: recovery replays events strictly after it.
 */
public record WorldSnapshot(long tick, long seq, String state) {

    public WorldSnapshot {
        Objects.requireNonNull(state, "state");
    }
}
