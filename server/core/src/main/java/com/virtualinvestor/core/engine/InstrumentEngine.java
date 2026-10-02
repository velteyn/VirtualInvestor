package com.virtualinvestor.core.engine;

import com.virtualinvestor.core.book.Fill;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.MatchResult;
import com.virtualinvestor.core.book.MatchingEngine;
import com.virtualinvestor.core.book.OrderBook;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.book.StopBook;
import com.virtualinvestor.core.book.StopOrder;
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

    public OrderBook book() {
        return book;
    }

    public StopBook stops() {
        return stops;
    }

    /** Submit an order and return every event it produced, in order. */
    public List<GameEvent> submit(IncomingOrder order, long tick) {
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.OrderAccepted(order.id(), order.participantId(), order.symbol(), tick));

        MatchResult result = matcher.match(book, order);
        if (result.rejected()) {
            events.add(new GameEvent.OrderRejected(
                    order.id(), order.participantId(), order.symbol(), result.rejection(), tick));
            return List.copyOf(events);
        }

        for (Fill fill : result.fills()) {
            lastPrice = fill.price();
            events.add(new GameEvent.TradePrinted(symbol, fill.price(), fill.quantity(), order.side(), tick));
            if (fill.makerRemainingAfter() == 0L) {
                events.add(new GameEvent.OrderFilled(
                        fill.makerOrderId(), symbol, fill.price(), fill.makerQuantity(), tick));
            } else {
                events.add(new GameEvent.OrderPartiallyFilled(
                        fill.makerOrderId(), symbol, fill.price(), fill.makerQuantity() - fill.makerRemainingAfter(), tick));
            }
        }

        long filled = result.filledQuantity();
        if (filled == order.quantity()) {
            events.add(new GameEvent.OrderFilled(order.id(), symbol, lastPrice, filled, tick));
        } else if (filled > 0L) {
            events.add(new GameEvent.OrderPartiallyFilled(order.id(), symbol, lastPrice, filled, tick));
        }

        if (result.rested()) {
            events.add(new GameEvent.OrderRested(
                    order.id(), symbol, order.limitPrice(), order.quantity() - filled, tick));
        } else if (order.type() == OrderType.MARKET && filled < order.quantity()) {
            events.add(new GameEvent.OrderCancelled(order.id(), symbol, "market remainder cancelled", tick));
        }

        if (filled > 0L || result.rested()) {
            emitTriggeredStops(tick, events);
        }
        return List.copyOf(events);
    }

    private void emitTriggeredStops(long tick, List<GameEvent> events) {
        for (IncomingOrder triggered : stops.onLastTradedPrice(lastPrice)) {
            events.add(new GameEvent.OrderTriggered(triggered.id(), symbol, lastPrice, tick));
            events.addAll(submit(triggered, tick));
        }
    }

    /** Cancel a working or untriggered order and return the resulting event, if any. */
    public List<GameEvent> cancel(String orderId, long tick) {
        RestingOrder resting = book.cancel(orderId);
        if (resting != null) {
            return List.of(new GameEvent.OrderCancelled(orderId, symbol, "user", tick));
        }
        StopOrder stop = stops.cancel(orderId);
        if (stop != null) {
            return List.of(new GameEvent.OrderCancelled(orderId, symbol, "user", tick));
        }
        return List.of();
    }

    /** Rest a plain order directly (used to seed a book in tests). */
    public long rest(RestingOrder order) {
        return book.add(order);
    }
}
