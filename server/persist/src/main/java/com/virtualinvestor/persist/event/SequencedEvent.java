package com.virtualinvestor.persist.event;

import com.virtualinvestor.core.event.GameEvent;

/** An event with its position in a round's ordered log. */
public record SequencedEvent(long seq, GameEvent event) {
}
