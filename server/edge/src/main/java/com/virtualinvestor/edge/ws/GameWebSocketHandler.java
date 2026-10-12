package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.game.RoundRuntimeService;
import com.virtualinvestor.edge.wire.WireMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final IdentityDirectory identities;
    private final SeatDirectory seats;
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();

    public GameWebSocketHandler(MarketBroadcaster broadcaster, RoundRuntimeService runtime,
            IdentityDirectory identities, SeatDirectory seats) {
        this.broadcaster = broadcaster;
        this.runtime = runtime;
        this.identities = identities;
        this.seats = seats;
    }

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

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
            // Only approved players hold a login token: a socket without an approved
            // identity watches but can never bind, so its commands are refused below.
            var identity = identities.identityForToken(join.sessionToken());
            if (identity.isEmpty()) {
                log.info("join refused (not-approved): round={} session={}", join.roundId(), session.getId());
                session.sendMessage(new TextMessage(WireCoder.toJson(
                        new WireMessage.ErrorMessage("not-approved",
                                "log in with an approved player to join a round"))));
                return;
            }
            // The binding follows the token's seat, never the stated participant id,
            // so each socket trades exactly as its own seat (multiplayer-seats 2.3).
            var seat = seats.seatFor(join.roundId(), identity.get().playerId());
            if (seat.isEmpty()) {
                log.info("join refused (no seat): round={} player={} session={}", join.roundId(),
                        identity.get().playerId(), session.getId());
                session.sendMessage(new TextMessage(WireCoder.toJson(
                        new WireMessage.ErrorMessage("not-approved",
                                "this player holds no seat in that round"))));
                return;
            }
            bindings.put(session.getId(), new Binding(join.roundId(), seat.get()));
            broadcaster.join(session, join.roundId());
            log.info("join bound: round={} seat={} player={} session={}", join.roundId(), seat.get(),
                    identity.get().playerId(), session.getId());
            return;
        }

        if (payload instanceof WireMessage.ResyncRequest resync) {
            Binding binding = bindings.get(session.getId());
            if (binding == null) {
                session.sendMessage(new TextMessage(WireCoder.toJson(
                        new WireMessage.ErrorMessage("not_joined", "join a round before resyncing"))));
                return;
            }
            String participantId = resync.participantId() == null || resync.participantId().isBlank()
                    ? binding.participantId()
                    : resync.participantId();
            try {
                var frames = runtime.snapshot(binding.roundId(), participantId);
                for (WireMessage.Payload frame : frames) {
                    session.sendMessage(new TextMessage(WireCoder.toJson(frame)));
                }
                log.info("resync served: round={} seat={} frames={}", binding.roundId(),
                        binding.participantId(), frames.size());
            } catch (RuntimeException e) {
                session.sendMessage(new TextMessage(WireCoder.toJson(
                        new WireMessage.ErrorMessage("resync_failed", e.getMessage()))));
            }
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
                log.info("order routed: round={} seat={} {} tick={}", binding.roundId(),
                        binding.participantId(), payload.getClass().getSimpleName(), tick);
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
