package com.virtualinvestor.core.pricing;

/**
 * Closed-form European option premia (design D3). A pure function of observable state —
 * same inputs always yield the same premium, so live runs and event-log replays agree.
 * The round is intraday with no risk-free rate in the simulation, so the rate is 0 and
 * put-call parity reads {@code C - P = S - K}.
 */
public final class OptionPricing {

    private OptionPricing() {
    }

    public static double call(double spot, double strike, double yearsToExpiry, double volatility) {
        return premium(spot, strike, yearsToExpiry, volatility, true);
    }

    public static double put(double spot, double strike, double yearsToExpiry, double volatility) {
        return premium(spot, strike, yearsToExpiry, volatility, false);
    }

    public static double premium(
            double spot, double strike, double yearsToExpiry, double volatility, boolean call) {
        requirePositive(spot, "spot");
        requirePositive(strike, "strike");
        if (!Double.isFinite(yearsToExpiry) || yearsToExpiry < 0.0) {
            throw new IllegalArgumentException("yearsToExpiry must be finite and non-negative");
        }
        if (!Double.isFinite(volatility) || volatility < 0.0) {
            throw new IllegalArgumentException("volatility must be finite and non-negative");
        }
        if (yearsToExpiry == 0.0 || volatility == 0.0) {
            double intrinsic = call ? Math.max(spot - strike, 0.0) : Math.max(strike - spot, 0.0);
            return intrinsic;
        }
        double sqrtT = Math.sqrt(yearsToExpiry);
        double d1 = (Math.log(spot / strike) + 0.5 * volatility * volatility * yearsToExpiry)
                / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;
        if (call) {
            return spot * normalCdf(d1) - strike * normalCdf(d2);
        }
        return strike * normalCdf(-d2) - spot * normalCdf(-d1);
    }

    /** Standard normal CDF via the Abramowitz-Stegun erf approximation (pure, no dependencies). */
    static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        double sign = x < 0.0 ? -1.0 : 1.0;
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double poly = ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592;
        return sign * (1.0 - poly * t * Math.exp(-ax * ax));
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be a positive finite price, was " + value);
        }
    }
}
