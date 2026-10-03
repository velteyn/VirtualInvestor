package com.virtualinvestor.runtime;

import com.virtualinvestor.core.bot.BotContext;
import com.virtualinvestor.core.bot.BotStrategy;
import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.bot.OrderIntent;
import com.virtualinvestor.core.bot.Quote;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.book.Fill;
import com.virtualinvestor.core.participant.Participant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives one virtual participant. A market-maker runner (MARK and the liquidity institutions)
 * cancels and reposts a two-sided quote on a cadence; a strategy runner evaluates its
 * {@link BotStrategy} and submits the resulting orders. State that matters across ticks (open
 * quote ids, the id counter) lives here so decisions stay deterministic.
 */
final class BotRunner {

    private static final long QUOTE_SIZE = 500L;

    /** Order size is at most this fraction of the participant's equity, in shares. */
    private static final double SIZE_FRACTION_OF_EQUITY = 0.02;
    /** Hard cap on a single order's size, in shares, regardless of equity. */
    private static final long MAX_ORDER_SIZE = 5_000L;

    private final Participant participant;
    private final BotStrategy strategy;
    private final MarketMakerStrategy maker;
    private final long quoteRefreshTicks;
    private final double tickSize;
    private final long baseSize;

    private final List<String> restingQuoteIds = new ArrayList<>();
    private long sequence;
    private boolean stopped;

    /** Size an order from equity: a fraction of equity (at a rough price), capped. */
    private long sizeForEquity(double equity, double price) {
        if (price <= 0.0) {
            return Math.min(baseSize, MAX_ORDER_SIZE);
        }
        long byEquity = (long) Math.floor(equity * SIZE_FRACTION_OF_EQUITY / price);
        return Math.max(1L, Math.min(byEquity, MAX_ORDER_SIZE));
    }

    BotRunner(Participant participant, BotStrategy strategy, MarketMakerStrategy maker, long quoteRefreshTicks) {
        this(participant, strategy, maker, quoteRefreshTicks, 0.01);
    }

    BotRunner(Participant participant, BotStrategy strategy, MarketMakerStrategy maker, long quoteRefreshTicks,
              double tickSize) {
        this.participant = participant;
        this.strategy = strategy;
        this.maker = maker;
        this.quoteRefreshTicks = quoteRefreshTicks;
        this.tickSize = tickSize;
        this.baseSize = strategy != null ? 100L : QUOTE_SIZE;
    }

    boolean stopped() {
        return stopped;
    }

    void stop() {
        this.stopped = true;
    }

    Participant participant() {
        return participant;
    }

    List<GameEvent> act(
            InstrumentEngine engine, double fundamental, double volatility, double priceChange,
            long netPosition, long tick, double equity, Consumer<Fill> onFill) {
        if (stopped) {
            return List.of();
        }
        if (maker != null) {
            if (tick % quoteRefreshTicks != 0L) {
                return List.of();
            }
            return refreshQuote(engine, fundamental, volatility, netPosition, tick, equity, onFill);
        }
        return take(engine, fundamental, volatility, priceChange, netPosition, tick, equity, onFill);
    }

    private List<GameEvent> refreshQuote(
            InstrumentEngine engine, double fundamental, double volatility, long netPosition, long tick,
            double equity, Consumer<Fill> onFill) {
        List<GameEvent> events = new ArrayList<>();
        for (String id : restingQuoteIds) {
            events.addAll(engine.cancel(id, tick));
        }
        restingQuoteIds.clear();

        Quote quote = maker.quote(fundamental, volatility, netPosition);
        long size = sizeForEquity(equity, fundamental);
        String symbol = engine.symbol();
        String bidId = participant.id() + "-" + symbol + "-b" + (sequence++);
        String askId = participant.id() + "-" + symbol + "-a" + (sequence++);
        submit(engine, new IncomingOrder(bidId, participant.id(), symbol, OrderSide.BUY, OrderType.LIMIT, size, round(quote.bidPrice())), tick, events, onFill);
        submit(engine, new IncomingOrder(askId, participant.id(), symbol, OrderSide.SELL, OrderType.LIMIT, size, round(quote.askPrice())), tick, events, onFill);
        restingQuoteIds.add(bidId);
        restingQuoteIds.add(askId);
        return events;
    }

    private List<GameEvent> take(
            InstrumentEngine engine, double fundamental, double volatility, double priceChange,
            long netPosition, long tick, double equity, Consumer<Fill> onFill) {
        BotContext context = new BotContext(
                engine.symbol(), engine.lastPrice(), fundamental, priceChange, volatility, netPosition,
                participant.account().cash(), null, tick);
        List<OrderIntent> intents = strategy.decide(context);
        if (intents.isEmpty()) {
            return List.of();
        }
        List<GameEvent> events = new ArrayList<>();
        String symbol = engine.symbol();
        long size = sizeForEquity(equity, engine.lastPrice());
        for (OrderIntent intent : intents) {
            long quantity = Math.min(intent.quantity(), size);
            String id = participant.id() + "-" + symbol + "-" + (sequence++);
            submit(engine, new IncomingOrder(
                    id, participant.id(), symbol, intent.side(), intent.type(), quantity, intent.price()),
                    tick, events, onFill);
        }
        return events;
    }

    /** Snap a price onto the instrument's tick grid so it is a valid order price. */
    private double round(double price) {
        return Math.round(price / tickSize) * tickSize;
    }

    private static void submit(
            InstrumentEngine engine, IncomingOrder order, long tick, List<GameEvent> events, Consumer<Fill> onFill) {
        InstrumentEngine.SubmitResult result = engine.submitWithFills(order, tick);
        for (Fill fill : result.fills()) {
            onFill.accept(fill);
        }
        events.addAll(result.events());
    }
}
