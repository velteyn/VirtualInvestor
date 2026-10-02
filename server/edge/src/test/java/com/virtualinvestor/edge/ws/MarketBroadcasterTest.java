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
}
