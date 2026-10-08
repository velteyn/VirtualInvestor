package com.virtualinvestor.persist.snapshot;

import java.util.Optional;

/** Durable store of {@link WorldSnapshot}s for a round. */
public interface SnapshotStore {

    void save(WorldSnapshot snapshot);

    /** The snapshot with the highest sequence number, if any. */
    Optional<WorldSnapshot> latest();

    /** Delete snapshots below the given tick (superseded checkpoints accumulate otherwise). */
    void deleteBefore(long tick);
}
