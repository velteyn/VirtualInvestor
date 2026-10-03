package com.virtualinvestor.edge.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.game.RoundBroadcaster;
import com.virtualinvestor.edge.game.RoundRuntimeService;
import com.virtualinvestor.edge.wire.WireMessage;
import com.virtualinvestor.runtime.RunningRound;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

/** Group 1: order commands sent over the socket reach the running round and events stream back. */
class WebSocketOrderDispatchTest {

    @Test
    void aJoinedSocketPlacesAnOrderOnTheRound() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RoundRuntimeService runtime = new RoundRuntimeService(new RoundBroadcaster(broadcaster));
        RunningRound round = runtime.start("r-ws", 5L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false, 60_000L);
        // Step into continuous trading so orders are accepted.
        for (int i = 0; i < 4; i++) {
            round.step();
        }

        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime);
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        handler.afterConnectionEstablished(session);

        // Join the round, then place an order.
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.JoinRound("r-ws", "human"))));
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.PlaceOrderCommand("t1", "NVDO", "BUY", "LIMIT", 10L, 1.0, 0.0))));

        assertEquals(1, round.pendingCommands(), "the command should be queued on the round");
    }

    @Test
    void orderEventsStreamBackToSubscribers() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RoundRuntimeService runtime = new RoundRuntimeService(new RoundBroadcaster(broadcaster));
        RunningRound round = runtime.start("r-ws2", 6L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false, 60_000L);
        for (int i = 0; i < 4; i++) {
            round.step();
        }

        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime);
        RecordingWebSocketSession session = new RecordingWebSocketSession();
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.JoinRound("r-ws2", "human"))));

        double ask = round.engines().get("NVDO").book().bestAskPrice();
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.PlaceOrderCommand("t2", "NVDO", "BUY", "LIMIT", 10L, ask, 0.0))));
        // The loop applies the command and the broadcaster streams the resulting events.
        round.step();

        assertTrue(session.sent().stream().anyMatch(s -> s.contains("\"type\":\"order\"")),
                "the order events should stream back to the subscriber");
    }
}
