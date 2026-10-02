package com.virtualinvestor.edge.api;

import com.virtualinvestor.core.book.DepthLevel;
import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.participant.Account;
import com.virtualinvestor.core.participant.Position;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.edge.wire.WireMessage;
import java.util.List;
import java.util.Locale;

/**
 * Translates core domain objects into wire DTOs at the edge (design D2/D7). Keeping the mapping
 * here means the wire contract is stable and the core has no dependency on the edge; a change
 * to a core record cannot silently reshape the protocol.
 */
public final class WireMapper {

    private WireMapper() {
    }

    public static WireMessage.SymbolDto symbol(Instrument instrument) {
        return new WireMessage.SymbolDto(
                instrument.ticker(), instrument.name(), instrument.sector(),
                instrument.isIndex() ? "Index" : "Equity");
    }

    public static WireMessage.LeaderboardEntryDto standing(Standing standing) {
        return new WireMessage.LeaderboardEntryDto(
                standing.rank(), standing.participantId(), standing.displayName(),
                standing.equity(), standing.startingCapital(), standing.returnFraction());
    }

    public static WireMessage.AccountDto account(Account account) {
        return new WireMessage.AccountDto(account.participantId(), account.cash(), account.realizedPnl());
    }

    public static WireMessage.PositionDto position(String participantId, Position position) {
        return new WireMessage.PositionDto(
                participantId, position.ticker(), position.netQty(), position.avgPrice());
    }

    public static WireMessage.DepthDto depth(String symbol, List<DepthLevel> bids, List<DepthLevel> asks,
                                             double ltp, long tick) {
        return new WireMessage.DepthDto(symbol, levels(bids), levels(asks), ltp, tick);
    }

    private static List<WireMessage.DepthDto.Level> levels(List<DepthLevel> levels) {
        return levels.stream()
                .map(level -> new WireMessage.DepthDto.Level(level.price(), level.quantity()))
                .toList();
    }

    // ── client commands -> core commands ──────────────────────────────────

    /**
     * Translate a client order command into a core {@link Command} for the named participant.
     * Client tokens double as command ids, so a retried command is idempotent at intake.
     */
    public static Command command(String participantId, WireMessage.Payload payload, long tick) {
        if (payload instanceof WireMessage.PlaceOrderCommand place) {
            return new Command.PlaceOrder(
                    tokenOr(place.clientToken(), participantId, tick, "place"),
                    participantId, place.symbol(), side(place.side()), type(place.type()),
                    place.quantity(), place.price(), place.triggerPrice(), tick);
        }
        if (payload instanceof WireMessage.ModifyOrderCommand modify) {
            return new Command.ModifyOrder(
                    tokenOr(modify.clientToken(), modify.orderId(), tick, "modify"),
                    modify.orderId(), modify.price(), modify.triggerPrice(), tick);
        }
        if (payload instanceof WireMessage.CancelOrderCommand cancel) {
            return new Command.CancelOrder(
                    tokenOr(cancel.clientToken(), cancel.orderId(), tick, "cancel"),
                    cancel.orderId(), tick);
        }
        throw new IllegalArgumentException("not an order command: " + payload.getClass().getSimpleName());
    }

    public static OrderSide side(String raw) {
        try {
            return OrderSide.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown order side: " + raw);
        }
    }

    public static OrderType type(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MARKET", "MKT" -> OrderType.MARKET;
            case "LIMIT", "LMT" -> OrderType.LIMIT;
            case "STOP", "SL" -> OrderType.STOP;
            case "STOP_LIMIT", "SL-M", "SLM" -> OrderType.STOP_LIMIT;
            default -> throw new IllegalArgumentException("unknown order type: " + raw);
        };
    }

    private static String tokenOr(String clientToken, String fallback, long tick, String kind) {
        return clientToken == null || clientToken.isBlank()
                ? fallback + "-" + kind + "-" + tick
                : clientToken;
    }

    /** Map a core event to a wire payload, or null when the event has no wire representation. */
    public static WireMessage.Payload event(GameEvent event) {
        if (event instanceof GameEvent.TradePrinted trade) {
            return new WireMessage.TradeDto(trade.symbol(), trade.price(), trade.quantity(),
                    trade.aggressorSide().name(), trade.tick());
        }
        if (event instanceof GameEvent.NewsReleased news) {
            return new WireMessage.NewsDto(news.newsId(), news.source(), news.scope().name(),
                    news.symbol(), news.sector(), news.headline(), news.body(), news.magnitude(),
                    news.durationTicks(), news.stage().name(), news.tick());
        }
        return null;
    }
}
