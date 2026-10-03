package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.game.RoundRuntimeService;
import com.virtualinvestor.edge.wire.WireMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The single WebSocket endpoint (design D7). On connect it greets the client and registers it for
 * broadcast; inbound messages are decoded and dispatched — order commands are routed to the round
 * the socket joined, so the terminal can trade over the same connection it watches.
 */
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final MarketBroadcaster broadcaster;
    private final RoundRuntimeService runtime;
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();

    public GameWebSocketHandler(MarketBroadcaster broadcaster, RoundRuntimeService runtime) {
        this.broadcaster = broadcaster;
        this.runtime = runtime;
    }

    private record Binding(String roundId, String participantId) {
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

        if (payload instanceof WireMessage.JoinRound join) {
            bindings.put(session.getId(), new Binding(join.roundId(), join.participantId()));
            return;
        }

        if (isOrderCommand(payload)) {
            Binding binding = bindings.get(session.getId());
            if (binding == null) {
                session.sendMessage(new TextMessage(WireCoder.toJson(
                        new WireMessage.ErrorMessage("not_joined", "join a round before placing orders"))));
                return;
            }
            try {
                long tick = runtime.round(binding.roundId())
                        .map(com.virtualinvestor.runtime.RunningRound::tick)
                        .orElse(0L);
                runtime.submit(binding.roundId(), binding.participantId(), payload, tick);
            } catch (RuntimeException e) {
                session.sendMessage(new TextMessage(WireCoder.toJson(
                        new WireMessage.ErrorMessage("rejected", e.getMessage()))));
            }
            return;
        }

        session.sendMessage(new TextMessage(WireCoder.toJson(
                new WireMessage.ErrorMessage("not_implemented", "no handler for " + payload.getClass().getSimpleName()))));
    }

    private static boolean isOrderCommand(WireMessage.Payload payload) {
        return payload instanceof WireMessage.PlaceOrderCommand
                || payload instanceof WireMessage.ModifyOrderCommand
                || payload instanceof WireMessage.CancelOrderCommand;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
        bindings.remove(session.getId());
    }
}
