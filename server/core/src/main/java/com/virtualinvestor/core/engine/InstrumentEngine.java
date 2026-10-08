package com.virtualinvestor.core.engine;

import com.virtualinvestor.core.book.Fill;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.MatchResult;
import com.virtualinvestor.core.book.MatchingEngine;
import com.virtualinvestor.core.book.OrderBook;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.book.StopBook;
import com.virtualinvestor.core.book.StopOrder;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Per-instrument matching facade that emits the order lifecycle and trade-print events
 * (design D13). It owns the book, the stop book and the last traded price, and returns the
 * events produced by each submission so the world can append them to the durable log.
 */
public final class InstrumentEngine {

    private final String symbol;
    private final OrderBook book;
    private final StopBook stops;
    private final MatchingEngine matcher;

    private double lastPrice;

    public InstrumentEngine(String symbol, double initialPrice) {
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.book = new OrderBook(symbol);
        this.stops = new StopBook(symbol);
        this.matcher = new MatchingEngine();
        this.lastPrice = initialPrice;
    }

    public String symbol() {
        return symbol;
    }

    public double lastPrice() {
        return lastPrice;
    }

    /** Establish the price from a call auction (opening/closing). */
    public void setLastPrice(double price) {
        this.lastPrice = price;
    }

    public OrderBook book() {
        return book;
    }

    public StopBook stops() {
        return stops;
    }

    /** The events and executions of one submission, including any stop-triggered children. */
    public record SubmitResult(List<GameEvent> events, List<Fill> fills) {
        public SubmitResult {
            events = List.copyOf(events);
            fills = List.copyOf(fills);
        }
    }

    /** Submit and return only the events (executions are dropped). */
    public List<GameEvent> submit(IncomingOrder order, long tick) {
        return submitWithFills(order, tick).events();
    }

    /** Submit and return both the events and every execution, so callers can apply fills. */
    public SubmitResult submitWithFills(IncomingOrder order, long tick) {
        List<GameEvent> events = new ArrayList<>();
        List<Fill> fills = new ArrayList<>();
        submitInto(order, tick, events, fills);
        return new SubmitResult(events, fills);
    }

    private void submitInto(IncomingOrder order, long tick, List<GameEvent> events, List<Fill> fills) {
        events.add(report(order, OrderStatus.WORKING, OrderEventKind.NEW, 0L, order.quantity(),
                order.limitPrice(), null, tick));

        MatchResult result = matcher.match(book, order);
        if (result.rejected()) {
            events.add(report(order, OrderStatus.REJECTED, OrderEventKind.REJECTED, 0L, order.quantity(),
                    order.limitPrice(), result.rejection(), tick));
            return;
        }

        for (Fill fill : result.fills()) {
            lastPrice = fill.price();
            fills.add(fill);
            events.add(new GameEvent.TradePrinted(symbol, fill.price(), fill.quantity(), order.side(), tick));
            // A self-contained report for the resting (maker) order, per FIX: it carries the
            // maker's own side, type, original quantity and running fill state.
            boolean makerFilled = fill.makerRemainingAfter() == 0L;
            events.add(new GameEvent.OrderReport(
                    fill.makerOrderId(), fill.makerParticipantId(), symbol, fill.makerSide(),
                    OrderType.LIMIT, fill.makerQuantity(), fill.price(),
                    fill.makerQuantity() - fill.makerRemainingAfter(), fill.makerRemainingAfter(),
                    makerFilled ? OrderStatus.FILLED : OrderStatus.PARTIAL,
                    makerFilled ? OrderEventKind.FILL : OrderEventKind.PARTIAL_FILL,
                    null, tick));
        }

        long filled = result.filledQuantity();
        long leaves = order.quantity() - filled;
        if (filled > 0L && leaves > 0L) {
            // The taker executed against resting orders but still has size left: report the
            // accumulated fill, then say what becomes of the remainder.
            events.add(report(order, OrderStatus.PARTIAL, OrderEventKind.PARTIAL_FILL,
                    filled, leaves, lastPrice, null, tick));
        }
        if (result.rested()) {
            if (filled == 0L) {
                events.add(report(order, OrderStatus.WORKING, OrderEventKind.RESTED,
                        0L, leaves, order.limitPrice(), null, tick));
            }
        } else if (leaves > 0L) {
            // Market and IOC remainders are cancelled outright rather than rested.
            events.add(report(order, OrderStatus.CANCELLED, OrderEventKind.CANCELLED,
                    filled, leaves, 0.0, "market remainder cancelled", tick));
        } else {
            events.add(report(order, OrderStatus.FILLED, OrderEventKind.FILL,
                    filled, 0L, lastPrice, null, tick));
        }

        if (filled > 0L || result.rested()) {
            for (IncomingOrder triggered : stops.onLastTradedPrice(lastPrice)) {
                events.add(new GameEvent.OrderReport(
                        triggered.id(), triggered.participantId(), symbol, triggered.side(), triggered.type(),
                        triggered.quantity(), triggered.limitPrice() > 0 ? triggered.limitPrice() : lastPrice,
                        0L, triggered.quantity(), OrderStatus.WORKING, OrderEventKind.TRIGGERED, null, tick));
                submitInto(triggered, tick, events, fills);
            }
        }
    }

    /** Build a self-contained report carrying the order's full identity and fill state. */
    private static GameEvent.OrderReport report(
            IncomingOrder order, OrderStatus status, OrderEventKind kind,
            long filled, long leaves, double price, String reason, long tick) {
        return new GameEvent.OrderReport(
                order.id(), order.participantId(), order.symbol(), order.side(), order.type(),
                order.quantity(), price, filled, leaves, status, kind, reason, tick);
    }

    /** Cancel a working or untriggered order and return the resulting event, if any. */
    public List<GameEvent> cancel(String orderId, long tick) {
        RestingOrder resting = book.cancel(orderId);
        if (resting != null) {
            return List.of(cancelReport(resting, "user", tick));
        }
        StopOrder stop = stops.cancel(orderId);
        if (stop != null) {
            return List.of(new GameEvent.OrderReport(
                    stop.id(), stop.participantId(), symbol, stop.side(), stop.type(),
                    stop.quantity(), stop.limitPrice(), 0L, stop.quantity(),
                    OrderStatus.CANCELLED, OrderEventKind.CANCELLED, "user", tick));
        }
        return List.of();
    }

    private GameEvent.OrderReport cancelReport(RestingOrder order, String reason, long tick) {
        return new GameEvent.OrderReport(
                order.id(), order.participantId(), symbol, order.side(), OrderType.LIMIT,
                order.quantity(), order.price(), order.filled(), order.remaining(),
                OrderStatus.CANCELLED, OrderEventKind.CANCELLED, reason, tick);
    }

    /** Rest a plain order directly (used to seed a book in tests). */
    public long rest(RestingOrder order) {
        return book.add(order);
    }
}
