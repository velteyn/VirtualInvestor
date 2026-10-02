package com.virtualinvestor.runtime;

import com.virtualinvestor.core.event.GameEvent;
import java.util.List;

/**
 * Durable sink for the world's event stream (design D5). The loop appends each tick's events and
 * asks for a checkpoint on a cadence; the implementation batches and never blocks the tick.
 * A no-op implementation lets the runtime run headlessly without a database.
 */
public interface WorldJournal {

    void append(List<GameEvent> events);

    void checkpoint(long tick);

    static WorldJournal noop() {
        return new WorldJournal() {
            @Override
            public void append(List<GameEvent> events) {
            }

            @Override
            public void checkpoint(long tick) {
            }
        };
    }
}
