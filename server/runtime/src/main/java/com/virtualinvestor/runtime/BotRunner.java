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
    private final long phaseTicks;
    /**
     * Session length in ticks, for session-relative replenishment (book-replenishment 1.1):
     * outer rungs requote at least a few times per session instead of on fixed slow
     * multiples that never fire in short sessions. {@link Long#MAX_VALUE} preserves the
     * legacy fixed quadratic cadence (used by unit tests pinning exact wave ticks).
     */
    private final long sessionTicks;

    /**
     * Refresh period of one ladder rung: quadratic in depth (touch fastest, outer rungs
     * linger) but capped so every rung fires at least ~3x per session. Pure function of
     * tick-math inputs, so replays agree.
     */
    static long rungPeriod(int level, long quoteRefreshTicks, long sessionTicks) {
        long tier = (level + 1L) * (level + 1L);
        long quadratic = quoteRefreshTicks * tier;
        if (sessionTicks <= 0L) {
            return quadratic;
        }
        long cap = Math.max(quoteRefreshTicks, sessionTicks / 3L);
        return Math.min(quadratic, cap);
    }

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
        this(participant, strategy, maker, quoteRefreshTicks, 0.01, 0L);
    }

    BotRunner(Participant participant, BotStrategy strategy, MarketMakerStrategy maker, long quoteRefreshTicks,
              double tickSize) {
        this(participant, strategy, maker, quoteRefreshTicks, tickSize, 0L);
    }

    BotRunner(Participant participant, BotStrategy strategy, MarketMakerStrategy maker, long quoteRefreshTicks,
              double tickSize, long phaseTicks) {
        this(participant, strategy, maker, quoteRefreshTicks, tickSize, phaseTicks, Long.MAX_VALUE);
    }

    BotRunner(Participant participant, BotStrategy strategy, MarketMakerStrategy maker, long quoteRefreshTicks,
              double tickSize, long phaseTicks, long sessionTicks) {
        this.participant = participant;
        this.strategy = strategy;
        this.maker = maker;
        this.quoteRefreshTicks = quoteRefreshTicks;
        this.tickSize = tickSize;
        this.phaseTicks = phaseTicks;
        this.sessionTicks = sessionTicks;
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
            long netPosition, long tick, double equity, double toxicity, Consumer<Fill> onFill) {
        if (stopped) {
            return List.of();
        }
        if (maker != null) {
            Quote quote = maker.quote(fundamental, volatility, netPosition, toxicity);
            if (!rungDue(quote, tick)) {
                return List.of();
            }
            return refreshQuote(engine, quote, fundamental, netPosition, tick, equity, onFill);
        }
        return take(engine, fundamental, volatility, priceChange, netPosition, tick, equity, onFill);
    }

    /** Whether any ladder rung's session-relative wave lands on this tick. */
    private boolean rungDue(Quote quote, long tick) {
        int levels = Math.max(quote.bidLevels().size(), quote.askLevels().size());
        for (int level = 0; level < levels; level++) {
            if ((tick + phaseTicks) % rungPeriod(level, quoteRefreshTicks, sessionTicks) == 0L) {
                return true;
            }
        }
        return false;
    }

    private List<GameEvent> refreshQuote(
            InstrumentEngine engine, Quote quote, double fundamental, long netPosition, long tick,
            double equity, Consumer<Fill> onFill) {
        List<GameEvent> events = new ArrayList<>();
        long size = sizeForEquity(equity, fundamental);
        String symbol = engine.symbol();

        // Desired ladder: touch first, deeper levels scaled by the equity-sized base.
        // Tier periods are session-relative (rungPeriod), so the touch leads and outer
        // levels linger yet still rebuild in-session (book-replenishment 1.1) while
        // 5-deep traffic stays under 2x the single-level baseline.
        List<DesiredLevel> desired = new ArrayList<>();
        for (int level = 0; level < quote.bidLevels().size(); level++) {
            if ((tick + phaseTicks) % rungPeriod(level, quoteRefreshTicks, sessionTicks) != 0L) {
                desired.add(null);
                continue;
            }
            Quote.Level rung = quote.bidLevels().get(level);
            desired.add(new DesiredLevel(OrderSide.BUY, round(rung.price()),
                    Math.round(rung.size() * size / (double) maker.baseSize())));
        }
        for (int level = 0; level < quote.askLevels().size(); level++) {
            if ((tick + phaseTicks) % rungPeriod(level, quoteRefreshTicks, sessionTicks) != 0L) {
                desired.add(null);
                continue;
            }
            Quote.Level rung = quote.askLevels().get(level);
            desired.add(new DesiredLevel(OrderSide.SELL, round(rung.price()),
                    Math.round(rung.size() * size / (double) maker.baseSize())));
        }

        // Rebuild wholesale when the ladder shape itself changed; otherwise diff
        // slot by slot and leave untouched levels resting (realistic-liquidity 1.1).
        if (restingQuoteIds.size() != desired.size()) {
            for (String id : restingQuoteIds) {
                events.addAll(engine.cancel(id, tick));
            }
            restingQuoteIds.clear();
        }
        while (restingQuoteIds.size() < desired.size()) {
            restingQuoteIds.add(null);
        }
        for (int slot = 0; slot < desired.size(); slot++) {
            DesiredLevel want = desired.get(slot);
            if (want == null) {
                continue;
            }
            String currentId = restingQuoteIds.get(slot);
            com.virtualinvestor.core.book.RestingOrder current =
                    currentId == null ? null : engine.book().get(currentId);
            if (want.size() <= 0L) {
                // Withdrawn rung: pull the resting order and post nothing.
                if (currentId != null) {
                    events.addAll(engine.cancel(currentId, tick));
                    restingQuoteIds.set(slot, null);
                }
                continue;
            }
            if (current != null && current.price() == want.price() && current.remaining() == want.size()) {
                continue;
            }
            if (currentId != null) {
                events.addAll(engine.cancel(currentId, tick));
            }
            String id = participant.id() + "-" + symbol + "-q" + (sequence++);
            submit(engine, new IncomingOrder(id, participant.id(), symbol, want.side(), OrderType.LIMIT,
                    want.size(), want.price()), tick, events, onFill);
            restingQuoteIds.set(slot, id);
        }
        return events;
    }

    /** One desired ladder rung. */
    private record DesiredLevel(OrderSide side, double price, long size) {
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
            if (intent.type() == OrderType.MARKET) {
                quantity = clampMarketTake(engine, intent.side(), quantity);
                if (quantity <= 0L) {
                    continue;
                }
            }
            String id = participant.id() + "-" + symbol + "-" + (sequence++);
            submit(engine, new IncomingOrder(
                    id, participant.id(), symbol, intent.side(), intent.type(), quantity, intent.price()),
                    tick, events, onFill);
        }
        return events;
    }

    /**
     * Depth-aware taking (book-replenishment 2.1): a market order that cannot fill at all
     * rejects with "no liquidity", so never send one into an empty touch — size to visible
     * depth instead, and stand aside when the spread has blown out (deep fade). Limit
     * orders rest and never reject, so they pass through untouched. Pure function of
     * resting state, so replays agree.
     */
    private static long clampMarketTake(InstrumentEngine engine, OrderSide side, long quantity) {
        OrderSide resting = side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
        long touch = engine.book().aggregateQuantity(resting, 1);
        if (touch <= 0L) {
            return 0L;
        }
        if (spreadBlownOut(engine)) {
            return 0L;
        }
        return Math.min(quantity, touch);
    }

    /** Spread wider than 16% (4x the 4% base full spread) only happens in deep fade. */
    private static final double MAX_TAKE_SPREAD_FRACTION = 0.16;

    private static boolean spreadBlownOut(InstrumentEngine engine) {
        Double bid = engine.book().bestBidPrice();
        Double ask = engine.book().bestAskPrice();
        if (bid == null || ask == null || bid <= 0.0) {
            return false;
        }
        double mid = (bid + ask) / 2.0;
        return (ask - bid) / mid > MAX_TAKE_SPREAD_FRACTION;
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
