package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.wire.WireMessage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Fans market-data, order/account and news events out to connected WebSocket sessions. Depth
 * updates are coalesced per symbol within a short window so a fast book does not flood clients
 * (design D3). This is the streaming half of the wire protocol (design D7).
 */
@Component
public class MarketBroadcaster {

    /** How long a depth update is held so rapid changes to one symbol collapse into one send. */
    private static final long DEPTH_COALESCE_MILLIS = 250L;

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicLong sent = new AtomicLong();
    private final java.util.Map<String, PendingDepth> pendingDepth = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public long sentCount() {
        return sent.get();
    }

    /** Broadcast a payload to every connected session. */
    public void broadcast(WireMessage.Payload payload) {
        send(payload);
    }

    /** Record a depth update, coalescing per symbol; the latest wins within the window. */
    public void depth(WireMessage.DepthDto depth) {
        long now = System.currentTimeMillis();
        PendingDepth pending = pendingDepth.get(depth.symbol());
        if (pending == null || now - pending.queuedAt() >= DEPTH_COALESCE_MILLIS) {
            pendingDepth.put(depth.symbol(), new PendingDepth(depth, now));
            send(depth);
        } else {
            pendingDepth.put(depth.symbol(), new PendingDepth(depth, pending.queuedAt()));
        }
    }

    private void send(WireMessage.Payload payload) {
        String json;
        try {
            json = WireCoder.toJson(payload);
        } catch (RuntimeException e) {
            return;
        }
        TextMessage message = new TextMessage(json);
        sent.incrementAndGet();
        for (WebSocketSession session : sessions) {
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
            }
        }
    }

    private record PendingDepth(WireMessage.DepthDto depth, long queuedAt) {
    }
}
