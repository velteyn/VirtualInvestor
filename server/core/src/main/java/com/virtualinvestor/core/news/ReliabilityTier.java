package com.virtualinvestor.core.news;

/**
 * How much a release can be trusted (rivals-news 1.1): the wire is the
 * newsroom's own copy, patrons and spies are second-hand and deny more often.
 * Rolled from the round seed, so the same seed deals the same tiers and the
 * same confirmations.
 */
public enum ReliabilityTier {
    WIRE(0.8),
    PATRON(0.6),
    SPY(0.35);

    private final double confirmBias;

    ReliabilityTier(double confirmBias) {
        this.confirmBias = confirmBias;
    }

    /** Confirmation probability for a rumor of this tier. Spy denies most. */
    public double confirmBias() {
        return confirmBias;
    }

    /**
     * Tier roll from a uniform draw: wire 60%, patron 30%, spy 10%.
     */
    public static ReliabilityTier roll(double draw) {
        if (draw < 0.0 || draw >= 1.0) {
            throw new IllegalArgumentException("draw must be within [0, 1): " + draw);
        }
        if (draw < 0.6) {
            return WIRE;
        }
        if (draw < 0.9) {
            return PATRON;
        }
        return SPY;
    }
}
