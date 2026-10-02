package com.virtualinvestor.core.fundamental;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A per-instrument volatility multiplier that news raises and time decays back toward 1.0.
 * A value above 1.0 means the instrument is trading more violently than its base volatility,
 * which is what widens market-making spreads and enlarges price swings after a shock.
 */
public final class VolatilityState {

    private final Map<String, Double> excess = new LinkedHashMap<>();
    private final double decayPerStep;

    public VolatilityState(double decayPerStep) {
        if (decayPerStep <= 0.0 || decayPerStep > 1.0) {
            throw new IllegalArgumentException("decayPerStep must be within (0, 1]");
        }
        this.decayPerStep = decayPerStep;
    }

    /** Multiplier for an instrument; 1.0 when calm. */
    public double multiplier(String ticker) {
        return 1.0 + excess.getOrDefault(ticker, 0.0);
    }

    /** Raise an instrument's volatility by a fraction (e.g. 0.5 raises the multiplier to 1.5). */
    public void bump(String ticker, double fraction) {
        if (fraction > 0.0) {
            excess.merge(ticker, fraction, Double::sum);
        }
    }

    public void bumpAll(Collection<String> tickers, double fraction) {
        for (String ticker : tickers) {
            bump(ticker, fraction);
        }
    }

    /** Decay all elevations one step toward calm. */
    public void step() {
        for (Map.Entry<String, Double> entry : excess.entrySet()) {
            entry.setValue(entry.getValue() * decayPerStep);
        }
    }
}
