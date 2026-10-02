package com.virtualinvestor.persist.session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Remembers each participant's session token and last observed tick for reconnect. */
public final class SessionRegistry {

    private final Map<String, SessionToken> tokens = new LinkedHashMap<>();
    private final Map<String, Long> lastTicks = new LinkedHashMap<>();

    public void register(String participantId, SessionToken token) {
        tokens.put(Objects.requireNonNull(participantId, "participantId"), Objects.requireNonNull(token, "token"));
    }

    public SessionToken tokenFor(String participantId) {
        return tokens.get(participantId);
    }

    public void observeTick(String participantId, long tick) {
        lastTicks.put(participantId, tick);
    }

    public long lastTick(String participantId) {
        return lastTicks.getOrDefault(participantId, 0L);
    }
}
