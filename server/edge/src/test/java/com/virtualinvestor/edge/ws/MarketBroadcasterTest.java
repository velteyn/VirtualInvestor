package com.virtualinvestor.edge.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.edge.wire.WireMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketBroadcasterTest {

    @Test
    void broadcastReachesEverySession() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession a = new RecordingWebSocketSession();
        RecordingWebSocketSession b = new RecordingWebSocketSession();
        broadcaster.register(a);
        broadcaster.register(b);
        assertEquals(2, broadcaster.sessionCount());

        broadcaster.broadcast(new WireMessage.TradeDto("ACME", 100.0, 10L, "BUY", 1L));

        assertEquals(1, a.sent().size());
        assertEquals(1, b.sent().size());
        assertTrue(a.sent().get(0).contains("\"type\":\"trade\""));
    }

    @Test
    void depthUpdatesAreCoalescedPerSymbolWithinTheWindow() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);

        broadcaster.depth(new WireMessage.DepthDto("ACME",
                List.of(new WireMessage.DepthDto.Level(99.9, 100L)),
                List.of(new WireMessage.DepthDto.Level(100.1, 100L)), 100.0, 1L));
        broadcaster.depth(new WireMessage.DepthDto("ACME",
                List.of(new WireMessage.DepthDto.Level(99.8, 200L)),
                List.of(new WireMessage.DepthDto.Level(100.2, 200L)), 100.0, 2L));

        // The second ACME update inside the window is held, not sent immediately.
        assertEquals(1, session.sent().size(), () -> "unexpected sends: " + session.sent());
    }

    @Test
    void unregisterStopsDelivery() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        broadcaster.unregister(session);

        broadcaster.broadcast(new WireMessage.TradeDto("ACME", 100.0, 10L, "BUY", 1L));
        assertTrue(session.sent().isEmpty());
        assertEquals(0, broadcaster.sessionCount());
    }

    @Test
    void concurrentBroadcastsFromSeveralRoundsNeverDropSessions() throws Exception {
        // Every live round publishes on its own driver thread: concurrent
        // sendMessage calls on one session must serialize, never throw-and-drop
        // the client (Tomcat rejects overlapping sends on a session).
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        List<StrictWebSocketSession> sessions = List.of(
                new StrictWebSocketSession(), new StrictWebSocketSession(),
                new StrictWebSocketSession(), new StrictWebSocketSession());
        sessions.forEach(broadcaster::register);

        int drivers = 8;
        int perDriver = 300;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(drivers);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(drivers);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        for (int d = 0; d < drivers; d++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perDriver; i++) {
                    broadcaster.broadcast(new WireMessage.TradeDto("ACME", 100.0, 10L, "BUY", 1L));
                }
                return null;
            });
        }
        assertTrue(ready.await(10L, java.util.concurrent.TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30L, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(sessions.size(), broadcaster.sessionCount(),
                "no session may be unsubscribed by concurrent sends");
        for (StrictWebSocketSession session : sessions) {
            assertEquals(drivers * perDriver, session.sent().size(),
                    "every session must receive every broadcast");
        }
    }

    /**
     * A session that rejects overlapping sends like a real container: at most one
     * sendMessage may be in flight, with a small sleep to widen the race window.
     */
    private static final class StrictWebSocketSession implements org.springframework.web.socket.WebSocketSession {

        private final List<String> sent = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final java.util.concurrent.atomic.AtomicInteger inFlight =
                new java.util.concurrent.atomic.AtomicInteger();

        List<String> sent() {
            return sent;
        }

        @Override
        public String getId() {
            return "strict-" + System.identityHashCode(this);
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) {
            if (!inFlight.compareAndSet(0, 1)) {
                throw new IllegalStateException("concurrent send on one session");
            }
            try {
                Thread.sleep(0L, 10000);
                sent.add((String) message.getPayload());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.set(0);
            }
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
        }

        @Override
        public void close(org.springframework.web.socket.CloseStatus status) {
        }

        @Override
        public java.net.URI getUri() {
            return java.net.URI.create("ws://localhost/ws");
        }

        @Override
        public org.springframework.http.HttpHeaders getHandshakeHeaders() {
            return new org.springframework.http.HttpHeaders();
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return new java.util.HashMap<>();
        }

        @Override
        public java.security.Principal getPrincipal() {
            return null;
        }

        @Override
        public java.net.InetSocketAddress getLocalAddress() {
            return new java.net.InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public java.net.InetSocketAddress getRemoteAddress() {
            return new java.net.InetSocketAddress("127.0.0.1", 50000);
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 8192;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 8192;
        }

        @Override
        public java.util.List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
            return List.of();
        }
    }
}
