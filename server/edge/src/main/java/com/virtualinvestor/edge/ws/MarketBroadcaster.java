package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.wire.WireMessage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Fans market-data, order/account and news events out to the WebSocket sessions bound to
 * each round. Fan-out is strictly per round (round-stream-isolation): a socket receives
 * only frames for the round it joined, so one game can never observe another. Depth
 * updates are coalesced per round and symbol within a short window so a fast book does
 * not flood clients (design D3). This is the streaming half of the wire protocol
 * (design D7).
 */
@Component
public class MarketBroadcaster {

    /** How long a depth update is held so rapid changes to one symbol collapse into one send. */
    private static final long DEPTH_COALESCE_MILLIS = 250L;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final java.util.Map<WebSocketSession, String> rounds = new ConcurrentHashMap<>();
    private final AtomicLong sent = new AtomicLong();
    private final java.util.Map<String, PendingDepth> pendingDepth = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
        rounds.remove(session);
    }

    /** Bind a socket to a round (rebind-safe); unbound sockets receive nothing. */
    public void join(WebSocketSession session, String roundId) {
        sessions.add(session);
        rounds.put(session, roundId);
    }

    /** Unbind a socket; it stays connected but receives nothing further. */
    public void leave(WebSocketSession session) {
        rounds.remove(session);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public long sentCount() {
        return sent.get();
    }

    /** Broadcast a payload to the sessions bound to its round. */
    public void broadcast(String roundId, WireMessage.Payload payload) {
        send(roundId, payload);
    }

    /** Record a depth update, coalescing per round and symbol; the latest wins. */
    public void depth(String roundId, WireMessage.DepthDto depth) {
        long now = System.currentTimeMillis();
        String key = roundId + "|" + depth.symbol();
        PendingDepth pending = pendingDepth.get(key);
        if (pending == null || now - pending.queuedAt() >= DEPTH_COALESCE_MILLIS) {
            pendingDepth.put(key, new PendingDepth(depth, now));
            send(roundId, depth);
        } else {
            pendingDepth.put(key, new PendingDepth(depth, pending.queuedAt()));
        }
    }

    private void send(String roundId, WireMessage.Payload payload) {
        String json;
        try {
            json = WireCoder.toJson(payload);
        } catch (RuntimeException e) {
            return;
        }
        TextMessage message = new TextMessage(json);
        sent.incrementAndGet();
        for (WebSocketSession session : sessions) {
            if (!roundId.equals(rounds.get(session))) {
                continue;
            }
            try {
                // One session, one writer at a time: every live round publishes on
                // its own driver thread, and concurrent sendMessage calls on the
                // same session corrupt the stream (or throw and unsubscribe the
                // client mid-session). Per-session locking serializes them.
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                sessions.remove(session);
                rounds.remove(session);
            }
        }
    }

    private record PendingDepth(WireMessage.DepthDto depth, long queuedAt) {
    }
}
