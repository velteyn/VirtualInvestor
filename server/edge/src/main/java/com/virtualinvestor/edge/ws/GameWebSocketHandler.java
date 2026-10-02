package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.wire.WireMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The single WebSocket endpoint (design D7). On connect it greets the client with the protocol
 * version and registers it for broadcast; inbound client messages are decoded and answered with
 * an error until the command handlers of Group 11's adapters are wired (the streaming half is
 * complete, the write half arrives with the terminal).
 */
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final MarketBroadcaster broadcaster;

    public GameWebSocketHandler(MarketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        broadcaster.register(session);
        session.sendMessage(new TextMessage(WireCoder.toJson(new WireMessage.ServerHello())));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WireMessage.Payload payload;
        try {
            payload = WireCoder.fromJson(message.getPayload());
        } catch (RuntimeException e) {
            session.sendMessage(new TextMessage(WireCoder.toJson(
                    new WireMessage.ErrorMessage("bad_message", "could not decode message"))));
            return;
        }
        session.sendMessage(new TextMessage(WireCoder.toJson(
                new WireMessage.ErrorMessage("not_implemented", "no handler for " + payload.getClass().getSimpleName()))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}
