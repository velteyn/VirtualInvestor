package com.virtualinvestor.core.instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lists the derivative series for a round (design D1, futures/options specs). One
 * expiry per round at the closing bell; one future per tradeable underlying plus the
 * index; ATM plus {@link #STRIKES_EACH_SIDE} strikes each side per tradeable equity,
 * in calls and puts. Everything derives from the base universe, so the same base
 * always lists the same series.
 */
public final class SeriesLister {

    /** Strikes listed above and below ATM, per option flavor. */
    public static final int STRIKES_EACH_SIDE = 3;

    /** Default initial margin for futures as a fraction of notional (WST canon: 20%). */
    public static final double DEFAULT_INITIAL_MARGIN = 0.20;

    private SeriesLister() {
    }

    public static List<Instrument> list(InstrumentUniverse base) {
        Objects.requireNonNull(base, "base");
        List<Instrument> series = new ArrayList<>();
        for (Instrument equity : base.equities()) {
            series.add(future(equity));
            series.addAll(options(equity));
        }
        base.index().ifPresent(index -> series.add(future(index)));
        return List.copyOf(series);
    }

    private static Instrument future(Instrument underlying) {
        return new Instrument(
                SeriesSymbols.futureSymbol(underlying.ticker()),
                underlying.name() + " Future",
                underlying.sector(),
                InstrumentType.FUTURE,
                0L,
                1.0,
                underlying.tickSize(),
                underlying.quantityGrid(),
                underlying.referencePrice(),
                underlying.baseVolatility(),
                underlying.marketBeta(),
                underlying.sectorBeta());
    }

    /** The at-the-money strike a listing centers on, snapped to the strike grid. */
    public static double atmStrike(double referencePrice, double tick) {
        return Math.rint(referencePrice / strikeStep(referencePrice, tick)) * strikeStep(referencePrice, tick);
    }

    private static List<Instrument> options(Instrument underlying) {
        double tick = underlying.tickSize();
        double step = strikeStep(underlying.referencePrice(), tick);
        double atm = atmStrike(underlying.referencePrice(), tick);
        List<Instrument> listed = new ArrayList<>();
        for (int offset = -STRIKES_EACH_SIDE; offset <= STRIKES_EACH_SIDE; offset++) {
            double strike = Math.rint((atm + offset * step) / tick) * tick;
            if (!(strike > 0.0)) {
                continue;
            }
            listed.add(option(underlying, strike, true));
            listed.add(option(underlying, strike, false));
        }
        return listed;
    }

    private static Instrument option(Instrument underlying, double strike, boolean call) {
        double ref = underlying.referencePrice();
        double intrinsic = call ? Math.max(ref - strike, 0.0) : Math.max(strike - ref, 0.0);
        String symbol = call
                ? SeriesSymbols.callSymbol(underlying.ticker(), strike)
                : SeriesSymbols.putSymbol(underlying.ticker(), strike);
        String name = underlying.name() + " " + SeriesSymbols.strikeText(strike) + (call ? " Call" : " Put");
        return new Instrument(
                symbol,
                name,
                underlying.sector(),
                InstrumentType.OPTION,
                0L,
                1.0,
                underlying.tickSize(),
                underlying.quantityGrid(),
                Math.max(intrinsic, underlying.tickSize()),
                underlying.baseVolatility(),
                underlying.marketBeta(),
                underlying.sectorBeta());
    }

    /**
     * A human-friendly strike step: the largest 1/2/5-decimal step at or below 5% of the
     * reference price, snapped to a multiple of the tick. Deterministic by construction.
     */
    public static double strikeStep(double referencePrice, double tick) {
        double target = referencePrice * 0.05;
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(target)));
        double step = magnitude;
        for (double candidate : new double[] {5.0 * magnitude, 2.0 * magnitude, magnitude}) {
            if (candidate <= target) {
                step = candidate;
                break;
            }
        }
        return Math.max(tick, Math.rint(step / tick) * tick);
    }
}
