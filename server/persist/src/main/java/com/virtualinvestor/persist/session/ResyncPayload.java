package com.virtualinvestor.persist.session;

import com.virtualinvestor.persist.event.SequencedEvent;
import java.util.List;

/**
 * Everything a reconnecting client needs to catch up: the participant's identity, the current
 * tick, the sequence number it has caught up to, and the events it missed.
 */
public record ResyncPayload(
        String participantId,
        long tick,
        long fromSeq,
        long toSeq,
        List<SequencedEvent> missedEvents) {

    public ResyncPayload {
        missedEvents = List.copyOf(missedEvents);
    }
}
