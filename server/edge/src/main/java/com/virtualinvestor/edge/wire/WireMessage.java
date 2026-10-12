package com.virtualinvestor.edge.wire;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

/**
 * The versioned wire protocol (design D7). One envelope type carries every message the client
 * and server exchange; each message is a record. The `type` discriminator is stable and the
 * whole set is versioned by {@link #PROTOCOL_VERSION}. Field names are camelCase and machine-
 * generated; the Java core's record shapes are NOT the wire contract.
 */
public interface WireMessage {

    String PROTOCOL_VERSION = "1";

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = BarDto.class, name = "bar"),
            @JsonSubTypes.Type(value = DepthDto.class, name = "depth"),
            @JsonSubTypes.Type(value = TradeDto.class, name = "trade"),
            @JsonSubTypes.Type(value = OrderDto.class, name = "order"),
            @JsonSubTypes.Type(value = PositionDto.class, name = "position"),
            @JsonSubTypes.Type(value = AccountDto.class, name = "account"),
            @JsonSubTypes.Type(value = FillDto.class, name = "fill"),
            @JsonSubTypes.Type(value = MarkoutDto.class, name = "markout"),
            @JsonSubTypes.Type(value = NewsDto.class, name = "news"),
            @JsonSubTypes.Type(value = LeaderboardEntryDto.class, name = "leaderboardEntry"),
            @JsonSubTypes.Type(value = SymbolDto.class, name = "symbol"),
            @JsonSubTypes.Type(value = RoundDto.class, name = "round"),
            @JsonSubTypes.Type(value = PlaceOrderCommand.class, name = "placeOrder"),
            @JsonSubTypes.Type(value = ModifyOrderCommand.class, name = "modifyOrder"),
            @JsonSubTypes.Type(value = CancelOrderCommand.class, name = "cancelOrder"),
            @JsonSubTypes.Type(value = JoinRound.class, name = "joinRound"),
            @JsonSubTypes.Type(value = ResyncRequest.class, name = "resync"),
            @JsonSubTypes.Type(value = ResyncResult.class, name = "resyncResult"),
            @JsonSubTypes.Type(value = ServerHello.class, name = "hello"),
            @JsonSubTypes.Type(value = ErrorMessage.class, name = "error")
    })
    interface Payload {
    }

    /**
     * A client request to place an order. The order type is `orderType` because the JSON
     * discriminator already occupies the `type` key.
     */
    @JsonTypeName("placeOrder")
    record PlaceOrderCommand(String clientToken, String symbol, String side, String orderType, long quantity,
                             double price, double triggerPrice) implements WireMessage.Payload {
    }

    /** A client request to modify a working order. */
    @JsonTypeName("modifyOrder")
    record ModifyOrderCommand(String clientToken, String orderId, double price, double triggerPrice)
            implements WireMessage.Payload {
    }

    /** A client request to cancel a working order. */
    @JsonTypeName("cancelOrder")
    record CancelOrderCommand(String clientToken, String orderId) implements WireMessage.Payload {
    }

    /** Sent on connect so the client learns the protocol version. */
    @JsonTypeName("hello")
    record ServerHello(String protocolVersion) implements WireMessage.Payload {
        public ServerHello() {
            this(PROTOCOL_VERSION);
        }
    }

    @JsonTypeName("error")
    record ErrorMessage(String code, String message) implements WireMessage.Payload {
    }

    @JsonTypeName("bar")
    record BarDto(String symbol, String interval, long time, double open, double high, double low, double close,
                  long volume) implements WireMessage.Payload {
    }

    @JsonTypeName("depth")
    record DepthDto(String symbol, List<Level> bids, List<Level> asks, double ltp, long tick)
            implements WireMessage.Payload {
        public record Level(double price, long qty) {
        }
    }

    @JsonTypeName("trade")
    record TradeDto(String symbol, double price, long quantity, String aggressor, long tick)
            implements WireMessage.Payload {
    }

    /**
     * An order update for the terminal's working-order book. The order type is `orderType`
     * because the JSON discriminator already occupies the `type` key (a `type` component
     * here would emit a duplicate key and hide the frame discriminator from the client).
     */
    @JsonTypeName("order")
    record OrderDto(String orderId, String participantId, String symbol, String side, String orderType, long quantity,
                    double price, double triggerPrice, String status, long filledQty, String reason, long tick)
            implements WireMessage.Payload {
    }

    @JsonTypeName("position")
    record PositionDto(String participantId, String symbol, long netQty, double avgPrice, double realized,
            double locked)
            implements WireMessage.Payload {
    }

    @JsonTypeName("account")
    record AccountDto(String participantId, double cash, double realizedPnl, double feesPaid,
            double lockedMargin)
            implements WireMessage.Payload {
    }

    /** One filled order for the portfolio history: cumulative fill plus the fee on it. */
    @JsonTypeName("fill")
    record FillDto(String orderId, String symbol, String side, String orderType, long quantity,
                   long filledQuantity, double price, double fee, long tick)
            implements WireMessage.Payload {
    }

    /**
     * One fill joined to the first print at or past the horizon (realistic-liquidity
     * 4.1): fill price, mark price and the signed markout, so execution quality is
     * readable over the wire without touching the journal.
     */
    @JsonTypeName("markout")
    record MarkoutDto(String orderId, String symbol, String side, double fillPrice, double markPrice,
                      double markout, long fillTick, long markTick) implements WireMessage.Payload {
    }

    @JsonTypeName("news")
    record NewsDto(String newsId, String source, String scope, String symbol, String sector, String headline,
                   String body, double signedMagnitude, long durationTicks, String stage, long tick)
            implements WireMessage.Payload {
    }

    @JsonTypeName("leaderboardEntry")
    record LeaderboardEntryDto(int rank, String participantId, String displayName, double equity,
                               double startingCapital, double returnFraction)
            implements WireMessage.Payload {
    }

    @JsonTypeName("symbol")
    record SymbolDto(String ticker, String name, String sector, String assetClass) implements WireMessage.Payload {
    }

    @JsonTypeName("round")
    record RoundDto(String roundId, long seed, double startingCapital, long durationMinutes, String preset,
                    String state, String sessionToken) implements WireMessage.Payload {
    }

    /** A client request envelope: a request id plus a command-style payload. */
    record Request(String requestId, String type, Payload payload) {
    }

    /** A server event envelope: an optional sequence position plus a payload. */
    record Event(long seq, long tick, String type, Payload payload) {
    }

    /**
     * Binds this socket to a round and participant, so commands route to that round.
     * The login token is required: only approved players hold one, so sockets without
     * an approved identity are refused with {@code not-approved} and can only watch.
     */
    @JsonTypeName("joinRound")
    record JoinRound(String roundId, String participantId, String sessionToken) implements WireMessage.Payload {
    }

    /** A request to catch up from a sequence number after a dropped connection. */
    @JsonTypeName("resync")
    record ResyncRequest(String participantId, String sessionToken, long lastSeenSeq) implements WireMessage.Payload {
    }

    /** The events a reconnecting client missed, with the sequence it has now caught up to. */
    @JsonTypeName("resyncResult")
    record ResyncResult(long fromSeq, long toSeq, long tick, List<Event> events) implements WireMessage.Payload {
        public ResyncResult {
            events = List.copyOf(events);
        }
    }
}
