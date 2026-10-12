package com.virtualinvestor.core.pricing;

import java.util.OptionalDouble;

/**
 * Mark rules for derivative series (design D2/D3): prefer the last traded price so marks
 * never drift from executable levels; fall back to a derived reference for untraded
 * series. Intraday cost of carry is zero (no rates or dividends in the simulation), so
 * the futures reference is the underlying spot price.
 */
public final class SeriesMarks {

    private SeriesMarks() {
    }

    /** Futures fair reference: spot plus intraday carry (zero). */
    public static double futuresReference(double spot) {
        if (!Double.isFinite(spot) || spot <= 0.0) {
            throw new IllegalArgumentException("spot must be a positive finite price, was " + spot);
        }
        return spot;
    }

    /** Futures mark: last trade when present, otherwise the spot reference. */
    public static double futuresMark(OptionalDouble lastTraded, double spot) {
        if (lastTraded != null && lastTraded.isPresent()) {
            return lastTraded.getAsDouble();
        }
        return futuresReference(spot);
    }

    /** Option mark: last traded premium when present, otherwise the formula value. */
    public static double optionMark(OptionalDouble lastTraded, double formulaPremium) {
        if (lastTraded != null && lastTraded.isPresent()) {
            return lastTraded.getAsDouble();
        }
        if (!Double.isFinite(formulaPremium) || formulaPremium < 0.0) {
            throw new IllegalArgumentException("formulaPremium must be finite and non-negative");
        }
        return formulaPremium;
    }

    /**
     * Derived quoting reference for a series: spot for futures, formula premium
     * for options. Empty when the spot is unusable (non-positive or non-finite),
     * so callers skip the quote instead of throwing mid-tick.
     */
    public static OptionalDouble seriesReference(
            com.virtualinvestor.core.instrument.SeriesSymbols.SeriesTerms terms,
            double spot, double yearsLeft, double volatility) {
        if (!Double.isFinite(spot) || spot <= 0.0) {
            return OptionalDouble.empty();
        }
        if (terms.kind() == com.virtualinvestor.core.instrument.SeriesSymbols.SeriesKind.FUTURE) {
            return OptionalDouble.of(futuresReference(spot));
        }
        if (!Double.isFinite(yearsLeft) || yearsLeft < 0.0
                || !Double.isFinite(volatility) || volatility < 0.0) {
            return OptionalDouble.empty();
        }
        boolean call = terms.kind() == com.virtualinvestor.core.instrument.SeriesSymbols.SeriesKind.CALL;
        return OptionalDouble.of(OptionPricing.premium(spot, terms.strike(), yearsLeft, volatility, call));
    }
}
