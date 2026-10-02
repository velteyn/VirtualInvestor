package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.edge.ws.MarketBroadcaster;
import com.virtualinvestor.edge.ws.RecordingWebSocketSession;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 5.1: the world's events reach the broadcaster as wire payloads. */
class RoundBroadcasterTest {

    @Test
    void tradeNewsAndOrderEventsAreBroadcast() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster);

        InstrumentEngine engine = new InstrumentEngine("NVDO", 100.0);
        engine.rest(new RestingOrder("b1", "MARK", "NVDO", OrderSide.BUY, 99.9, 500L));
        bridge.bindEngines(() -> List.of(engine));

        bridge.publish(List.of(
                new GameEvent.TradePrinted("NVDO", 100.0, 10L, OrderSide.BUY, 5L),
                new GameEvent.NewsReleased("n1", "THE_WIRE", NewsScope.MACRO, null, null,
                        "Data beats", "body", 0.01, 300L, NewsStage.RUMOR, 5L),
                new GameEvent.OrderFilled("o1", "NVDO", 100.0, 10L, 5L)), 1L);

        String all = String.join("\n", session.sent());
        assertTrue(all.contains("\"type\":\"trade\""), all);
        assertTrue(all.contains("\"type\":\"news\""), all);
        assertTrue(all.contains("\"type\":\"order\""), all);
        assertTrue(all.contains("\"type\":\"depth\""), "a trade should also publish depth");
    }

    @Test
    void irrelevantEventsProduceNoFrames() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster);

        bridge.publish(List.of(new GameEvent.Tick(1L, 100L), new GameEvent.RoundPaused("r", 1L)), 1L);

        assertEquals(0, session.sent().size());
    }
}
