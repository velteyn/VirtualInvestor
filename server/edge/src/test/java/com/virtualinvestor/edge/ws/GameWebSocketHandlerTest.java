package com.virtualinvestor.edge.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.edge.wire.WireMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

class GameWebSocketHandlerTest {

    @Test
    void onConnectSendsTheProtocolHello() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster);
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.afterConnectionEstablished(session);

        assertEquals(1, session.sent().size());
        assertTrue(session.sent().get(0).contains("\"type\":\"hello\""));
        assertTrue(session.sent().get(0).contains(WireMessage.PROTOCOL_VERSION));
        assertEquals(1, broadcaster.sessionCount());
    }

    @Test
    void undecodableMessageReturnsAnError() throws Exception {
        GameWebSocketHandler handler = new GameWebSocketHandler(new MarketBroadcaster());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage("{not valid json"));

        assertEquals(1, session.sent().size());
        assertTrue(session.sent().get(0).contains("bad_message"));
    }

    @Test
    void decodedMessageWithoutAHandlerReturnsNotImplemented() throws Exception {
        GameWebSocketHandler handler = new GameWebSocketHandler(new MarketBroadcaster());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.TradeDto("ACME", 100.0, 10L, "BUY", 1L))));

        assertTrue(session.sent().get(0).contains("not_implemented"));
    }
}
