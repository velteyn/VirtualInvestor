package com.virtualinvestor.persist.projection;

import com.virtualinvestor.core.event.GameEvent;

/**
 * A read model derived by folding the event log. A projection is disposable: it can always be
 * rebuilt from the log, so it is never the source of truth (task 9.4).
 */
public interface Projection<T> {

    void apply(long seq, GameEvent event);

    T result();
}
