package com.virtualinvestor.edge.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.edge.game.RoundBroadcaster;
import com.virtualinvestor.edge.game.RoundRuntimeService;
import com.virtualinvestor.edge.wire.WireMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

class GameWebSocketHandlerTest {

    private static final String TOKEN = "token-1";

    private static SeatDirectory seats() {
        return (roundId, playerId) -> java.util.Optional.of("human");
    }

    private static IdentityDirectory identities() {
        return token -> TOKEN.equals(token)
                ? java.util.Optional.of(new IdentityDirectory.Identity("human"))
                : java.util.Optional.empty();
    }


    private static RoundRuntimeService runtime(MarketBroadcaster broadcaster) {
        return new RoundRuntimeService(new RoundBroadcaster(broadcaster));
    }

    @Test
    void onConnectSendsTheProtocolHello() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.afterConnectionEstablished(session);

        assertEquals(1, session.sent().size());
        assertTrue(session.sent().get(0).contains("\"type\":\"hello\""));
        assertTrue(session.sent().get(0).contains(WireMessage.PROTOCOL_VERSION));
        assertEquals(1, broadcaster.sessionCount());
    }

    @Test
    void undecodableMessageReturnsAnError() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage("{not valid json"));

        assertEquals(1, session.sent().size());
        assertTrue(session.sent().get(0).contains("bad_message"));
    }

    @Test
    void decodedMessageWithoutAHandlerReturnsNotImplemented() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.TradeDto("ACME", 100.0, 10L, "BUY", 1L))));

        assertTrue(session.sent().get(0).contains("not_implemented"));
    }

    @Test
    void orderCommandWithoutJoiningIsRefused() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.PlaceOrderCommand("t1", "NVDO", "BUY", "LIMIT", 10L, 100.0, 0.0))));

        assertTrue(session.sent().get(0).contains("not_joined"));
    }

    @Test
    void joinWithoutATokenIsRefusedAsNotApproved() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.JoinRound("r1", "human", null))));

        assertTrue(session.sent().get(0).contains("not-approved"));
    }

    @Test
    void joinWithAnUnknownTokenIsRefusedAsNotApproved() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.JoinRound("r1", "human", "no-such-token"))));
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.PlaceOrderCommand("t9", "NVDO", "BUY", "LIMIT", 10L, 100.0, 0.0))));

        assertTrue(session.sent().get(0).contains("not-approved"));
        assertTrue(session.sent().get(1).contains("not_joined"),
                "without a binding the socket stays a spectator: " + session.sent());
    }

    @Test
    void joinWithAValidTokenButNoSeatIsRefused() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        SeatDirectory noSeats = (roundId, playerId) -> java.util.Optional.empty();
        GameWebSocketHandler handler =
                new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), noSeats);
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.JoinRound("r1", "human", TOKEN))));
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.PlaceOrderCommand("t9", "NVDO", "BUY", "LIMIT", 10L, 100.0, 0.0))));

        assertTrue(session.sent().get(0).contains("not-approved"));
        assertTrue(session.sent().get(1).contains("not_joined"),
                "without a seat binding the socket stays a spectator: " + session.sent());
    }

    @Test
    void resyncWithoutJoiningIsRefused() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, runtime(broadcaster), identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.ResyncRequest("human", "token", 0L))));

        assertTrue(session.sent().get(0).contains("not_joined"));
    }

    @Test
    void resyncReplaysWorkingOrdersPositionsAndAccount() throws Exception {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RoundRuntimeService service = runtime(broadcaster);
        GameWebSocketHandler handler = new GameWebSocketHandler(broadcaster, service, identities(), seats());
        RecordingWebSocketSession session = new RecordingWebSocketSession();

        com.virtualinvestor.runtime.RunningRound round = service.start("resync-1", 71L,
                new com.virtualinvestor.core.game.LaunchConfig(100_000.0, 60L,
                        com.virtualinvestor.core.rules.RealismPreset.REALISTIC),
                false);
        // A resting order owned by the reconnecting participant.
        round.engines().get("NVDO").rest(new com.virtualinvestor.core.book.RestingOrder(
                "w1", "human", "NVDO", com.virtualinvestor.core.domain.OrderSide.BUY, 90.0, 100L));

        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.JoinRound("resync-1", "human", TOKEN))));
        handler.handleTextMessage(session, new TextMessage(WireCoder.toJson(
                new WireMessage.ResyncRequest("human", "token", 0L))));

        String all = String.join("\n", session.sent());
        assertTrue(all.contains("\"orderId\":\"w1\""), "the working order is replayed: " + all);
        assertTrue(all.contains("\"orderType\":\"LIMIT\""), all);
        assertTrue(all.contains("\"type\":\"account\""), "the account frame is replayed: " + all);
        assertTrue(all.contains("\"feesPaid\""), "the account frame carries fees paid: " + all);
    }
}
