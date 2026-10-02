package com.virtualinvestor.persist.session;

import com.virtualinvestor.persist.event.EventLog;
import com.virtualinvestor.persist.event.SequencedEvent;
import java.util.List;
import java.util.Objects;

/**
 * Reclaims a seat after a dropped connection. It verifies the participant's token, then returns
 * the events the client missed since the sequence it last saw, so the client can reconcile its
 * order book, positions, account and market view without a full reload.
 */
public final class ReconnectService {

    private final EventLog log;
    private final SessionRegistry registry;

    public ReconnectService(EventLog log, SessionRegistry registry) {
        this.log = Objects.requireNonNull(log, "log");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * @param participantId the seat being reclaimed
     * @param rawToken      the token presented by the client
     * @param lastSeenSeq   the last sequence number the client already had (0 for none)
     * @return the catch-up payload
     * @throws SecurityException when the token does not match the seat
     */
    public ResyncPayload reconnect(String participantId, String rawToken, long lastSeenSeq) {
        if (lastSeenSeq < 0L) {
            throw new IllegalArgumentException("lastSeenSeq must not be negative");
        }
        SessionToken stored = registry.tokenFor(participantId);
        if (stored == null || !stored.matches(rawToken)) {
            throw new SecurityException("session token does not match participant " + participantId);
        }
        List<SequencedEvent> missed = log.readFrom(lastSeenSeq + 1L);
        long toSeq = missed.isEmpty() ? lastSeenSeq : missed.get(missed.size() - 1).seq();
        long tick = missed.isEmpty()
                ? registry.lastTick(participantId)
                : missed.get(missed.size() - 1).event().tick();
        return new ResyncPayload(participantId, tick, lastSeenSeq + 1L, toSeq, missed);
    }
}
