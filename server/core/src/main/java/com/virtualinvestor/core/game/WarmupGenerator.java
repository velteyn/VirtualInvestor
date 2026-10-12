package com.virtualinvestor.core.game;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.bot.Quote;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.instrument.SeriesLister;
import com.virtualinvestor.core.instrument.SeriesSymbols;
import com.virtualinvestor.core.market.Bar;
import com.virtualinvestor.core.pricing.OptionPricing;
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

    /**
     * Derived warm-up history for every listed derivative series (design D1):
     * futures copy underlying OHLC (intraday carry is zero), options map the
     * deterministic premium formula over underlying OHLC with high/low inverted
     * for puts. Volumes are zero — no activity is invented. A pure function of
     * its inputs: no RNG, so replays reproduce it exactly.
     */
    public static Map<String, List<Bar>> seriesHistory(
            InstrumentUniverse universe, Map<String, List<Bar>> spotHistory, double yearsToBell) {
        Objects.requireNonNull(universe, "universe");
        Objects.requireNonNull(spotHistory, "spotHistory");
        if (!Double.isFinite(yearsToBell) || yearsToBell < 0.0) {
            throw new IllegalArgumentException("yearsToBell must be finite and non-negative");
        }
        Map<String, List<Bar>> derived = new LinkedHashMap<>();
        for (Instrument series : SeriesLister.list(universe)) {
            SeriesSymbols.SeriesTerms terms = SeriesSymbols.parse(series.ticker()).orElseThrow();
            Instrument underlying = universe.find(terms.underlying()).orElseThrow();
            List<Bar> source = spotHistory.get(terms.underlying());
            if (source == null) {
                continue;
            }
            List<Bar> bars = new ArrayList<>(source.size());
            for (Bar bar : source) {
                bars.add(seriesBar(series, terms, underlying, bar, yearsToBell));
            }
            derived.put(series.ticker(), List.copyOf(bars));
        }
        return derived;
    }

    private static Bar seriesBar(Instrument series, SeriesSymbols.SeriesTerms terms, Instrument underlying,
            Bar bar, double yearsToBell) {
        double tick = series.tickSize();
        if (terms.kind() == SeriesSymbols.SeriesKind.FUTURE) {
            return new Bar(bar.timeSec(), snap(bar.open(), tick), snap(bar.high(), tick), snap(bar.low(), tick),
                    snap(bar.close(), tick), 0L);
        }
        double vol = underlying.baseVolatility();
        boolean call = terms.kind() == SeriesSymbols.SeriesKind.CALL;
        double open = OptionPricing.premium(bar.open(), terms.strike(), yearsToBell, vol, call);
        double high = OptionPricing.premium(bar.high(), terms.strike(), yearsToBell, vol, call);
        double low = OptionPricing.premium(bar.low(), terms.strike(), yearsToBell, vol, call);
        double close = OptionPricing.premium(bar.close(), terms.strike(), yearsToBell, vol, call);
        if (call) {
            return new Bar(bar.timeSec(), open, Math.max(high, Math.max(open, close)),
                    Math.min(low, Math.min(open, close)), close, 0L);
        }
        return new Bar(bar.timeSec(), open, Math.max(low, Math.max(open, close)),
                Math.min(high, Math.min(open, close)), close, 0L);
    }

    /** Rest a two-sided quote from the market maker so the book is non-empty at the open. */    public void seedBooks(Collection<InstrumentEngine> engines, FundamentalModel model) {
        seedBooks(engines, model, "MARK");
    }

    /** As above, but the resting quotes belong to the given participant (so fills credit it). */    public void seedBooks(Collection<InstrumentEngine> engines, FundamentalModel model, String participantId) {
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
