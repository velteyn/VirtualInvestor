package com.virtualinvestor.core.game;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.bot.Quote;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.market.Bar;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prepares the world before trading opens (task 8.3): generates a history of OHLCV bars from
 * the fundamental process so charts and indicators have data on tick one, and seeds each
 * instrument's book with a two-sided market-maker quote so it is never empty at the open.
 */
public final class WarmupGenerator {

    private final MarketMakerStrategy maker;
    private final long stepMillis;
    private final double volumeScale;

    public WarmupGenerator(MarketMakerStrategy maker, long stepMillis, double volumeScale) {
        this.maker = Objects.requireNonNull(maker, "maker");
        if (stepMillis <= 0L) {
            throw new IllegalArgumentException("stepMillis must be positive");
        }
        this.stepMillis = stepMillis;
        this.volumeScale = volumeScale;
    }

    /** Generate {@code barCount} bars per instrument, each covering {@code barTicks} steps. */
    public Map<String, List<Bar>> generateHistory(
            InstrumentUniverse universe, FundamentalModel model, long barCount, long barTicks, long startTimeSec) {
        if (barCount <= 0L || barTicks <= 0L) {
            throw new IllegalArgumentException("barCount and barTicks must be positive");
        }
        List<Instrument> instruments = universe.spots();
        Map<String, List<Bar>> history = new LinkedHashMap<>();
        for (Instrument instrument : instruments) {
            history.put(instrument.ticker(), new ArrayList<>());
        }
        int n = instruments.size();
        long secondsPerBar = Math.max(1L, barTicks * stepMillis / 1000L);

        for (long b = 0L; b < barCount; b++) {
            double[] open = new double[n];
            double[] high = new double[n];
            double[] low = new double[n];
            double[] close = new double[n];
            double[] volume = new double[n];
            for (int i = 0; i < n; i++) {
                double fundamental = model.fundamental(instruments.get(i).ticker());
                open[i] = fundamental;
                high[i] = fundamental;
                low[i] = fundamental;
                close[i] = fundamental;
            }
            for (long step = 0L; step < barTicks; step++) {
                model.step();
                for (int i = 0; i < n; i++) {
                    String ticker = instruments.get(i).ticker();
                    double fundamental = model.fundamental(ticker);
                    high[i] = Math.max(high[i], fundamental);
                    low[i] = Math.min(low[i], fundamental);
                    close[i] = fundamental;
                    volume[i] += Math.abs(model.lastReturn(ticker));
                }
            }
            long time = startTimeSec + b * secondsPerBar;
            for (int i = 0; i < n; i++) {
                history.get(instruments.get(i).ticker())
                        .add(new Bar(time, open[i], high[i], low[i], close[i], Math.round(volume[i] * volumeScale)));
            }
        }
        return history;
    }

    /** Rest a two-sided quote from the market maker so the book is non-empty at the open. */
    public void seedBooks(Collection<InstrumentEngine> engines, FundamentalModel model) {
        seedBooks(engines, model, "MARK");
    }

    /** As above, but the resting quotes belong to the given participant (so fills credit it). */
    public void seedBooks(Collection<InstrumentEngine> engines, FundamentalModel model, String participantId) {
        for (InstrumentEngine engine : engines) {
            double fundamental = model.fundamental(engine.symbol());
            Quote quote = maker.quote(fundamental, 0.0, 0L);
            double tick = tickFor(engine.symbol());
            engine.rest(new RestingOrder("warm-bid-" + engine.symbol(), participantId, engine.symbol(),
                    OrderSide.BUY, snap(quote.bidPrice(), tick), quote.bidSize()));
            engine.rest(new RestingOrder("warm-ask-" + engine.symbol(), participantId, engine.symbol(),
                    OrderSide.SELL, snap(quote.askPrice(), tick), quote.askSize()));
        }
    }

    private static double snap(double price, double tick) {
        return Math.round(price / tick) * tick;
    }

    private double tickFor(String symbol) {
        return InstrumentUniverse.defaultUniverse().find(symbol).map(Instrument::tickSize).orElse(0.01);
    }
}
