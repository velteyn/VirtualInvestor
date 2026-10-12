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
}
