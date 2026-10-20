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
        broadcaster.join(session, "r1");
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster, "r1");

        InstrumentEngine engine = new InstrumentEngine("NVDO", 100.0);
        engine.rest(new RestingOrder("b1", "MARK", "NVDO", OrderSide.BUY, 99.9, 500L));
        bridge.bindEngines(() -> List.of(engine));

        bridge.publish(List.of(
                new GameEvent.TradePrinted("NVDO", 100.0, 10L, OrderSide.BUY, 5L),
                new GameEvent.NewsReleased("n1", "THE_WIRE", NewsScope.MACRO, null, null,
                        "Data beats", "body", 0.01, 300L, NewsStage.RUMOR, 5L,
                        com.virtualinvestor.core.news.ReliabilityTier.WIRE),
                new GameEvent.OrderReport("o1", "p1", "NVDO", OrderSide.BUY,
                        com.virtualinvestor.core.domain.OrderType.LIMIT, 10L, 100.0, 10L, 0L,
                        com.virtualinvestor.core.domain.OrderStatus.FILLED,
                        com.virtualinvestor.core.domain.OrderEventKind.FILL, null, 5L)), 1L);

        String all = String.join("\n", session.sent());
        assertTrue(all.contains("\"type\":\"trade\""), all);
        assertTrue(all.contains("\"type\":\"news\""), all);
        assertTrue(all.contains("\"type\":\"order\""), all);
        assertTrue(all.contains("\"type\":\"depth\""), "a trade should also publish depth");

        // The order frame must carry the order's own type under `orderType`: a `type`
        // component would emit a duplicate key and hide the frame discriminator.
        String orderFrame = session.sent().stream().filter(frame -> frame.contains("\"orderId\"")).findFirst()
                .orElseThrow(() -> new AssertionError("expected an order frame in " + all));
        assertTrue(orderFrame.contains("\"orderType\":\"LIMIT\""), orderFrame);
        assertTrue(orderFrame.contains("\"side\":\"BUY\""), orderFrame);
        assertTrue(orderFrame.contains("\"status\":\"FILLED\""), orderFrame);
        long typeKeys = java.util.regex.Pattern.compile("\"type\"\\s*:").matcher(orderFrame).results().count();
        assertEquals(1, typeKeys, "the frame discriminator must be the only `type` key: " + orderFrame);
    }

    @Test
    void rejectedReportsCarryTheirReason() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        broadcaster.join(session, "r1");
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster, "r1");
        bridge.bindEngines(List::of);

        bridge.publish(List.of(
                new GameEvent.OrderReport("o9", "p1", "NVDO", OrderSide.BUY,
                        com.virtualinvestor.core.domain.OrderType.MARKET, 5L, 0.0, 0L, 5L,
                        com.virtualinvestor.core.domain.OrderStatus.REJECTED,
                        com.virtualinvestor.core.domain.OrderEventKind.REJECTED,
                        "no liquidity for market order", 7L)), 1L);

        String all = String.join("\n", session.sent());
        assertTrue(all.contains("\"status\":\"REJECTED\""), all);
        assertTrue(all.contains("no liquidity for market order"), all);
    }

    @Test
    void positionAndAccountUpdatesStreamAsFrames() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        broadcaster.join(session, "r1");
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster, "r1");
        bridge.bindEngines(List::of);

        bridge.publish(List.of(
                new GameEvent.PositionUpdated("p1", "NVDO", 10L, 640.0, 25.0, 0.0, 9L),
                new GameEvent.AccountUpdated("p1", 93_600.0, 25.0, 4.0, 500.0, 9L)), 1L);

        String all = String.join("\n", session.sent());
        assertTrue(all.contains("\"type\":\"position\""), all);
        assertTrue(all.contains("\"type\":\"account\""), all);
        assertTrue(all.contains("640.0"), all);
        assertTrue(all.contains("93600.0"), all);
        assertTrue(all.contains("500.0"), all);
    }

    @Test
    void closedBarsAreBroadcastAsBarFrames() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        broadcaster.join(session, "r1");
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster, "r1");
        bridge.bindEngines(List::of);

        bridge.publish(List.of(
                new GameEvent.BarClosed("NVDO", "6s", 1_700_000_360L, 640.0, 641.5, 639.0, 641.0, 1200L, 60L)), 1L);

        String all = String.join("\n", session.sent());
        assertTrue(all.contains("\"type\":\"bar\""), all);
        assertTrue(all.contains("\"symbol\":\"NVDO\""), all);
        assertTrue(all.contains("641.5"), all);
    }

    @Test
    void irrelevantEventsProduceNoFrames() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        broadcaster.register(session);
        broadcaster.join(session, "r1");
        RoundBroadcaster bridge = new RoundBroadcaster(broadcaster, "r1");

        bridge.publish(List.of(new GameEvent.Tick(1L, 100L), new GameEvent.RoundPaused("r", 1L)), 1L);

        assertEquals(0, session.sent().size());
    }

    @Test
    void bridgesConfineFramesToTheirOwnRound() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession a = new RecordingWebSocketSession();
        RecordingWebSocketSession b = new RecordingWebSocketSession();
        broadcaster.register(a);
        broadcaster.register(b);
        broadcaster.join(a, "r1");
        broadcaster.join(b, "r2");
        RoundBroadcaster one = new RoundBroadcaster(broadcaster, "r1");
        RoundBroadcaster two = new RoundBroadcaster(broadcaster, "r2");
        one.bindEngines(List::of);
        two.bindEngines(List::of);

        one.publish(List.of(new GameEvent.TradePrinted("NVDO", 100.0, 10L, OrderSide.BUY, 5L)), 1L);
        two.publish(List.of(new GameEvent.NewsReleased("n9", "THE_WIRE", NewsScope.MACRO, null, null,
                "Other game moves", "body", 0.01, 300L, NewsStage.RUMOR, 5L,
                com.virtualinvestor.core.news.ReliabilityTier.WIRE)), 1L);

        String aFrames = String.join("\n", a.sent());
        String bFrames = String.join("\n", b.sent());
        assertTrue(aFrames.contains("\"type\":\"trade\""), aFrames);
        assertTrue(!aFrames.contains("Other game moves"), aFrames);
        assertTrue(bFrames.contains("Other game moves"), bFrames);
        assertTrue(!bFrames.contains("\"type\":\"trade\""), bFrames);
    }
}
