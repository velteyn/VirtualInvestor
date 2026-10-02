package com.virtualinvestor.core.rules;

/**
 * A realism setting chosen on the launch pad. It toggles the market rules the world applies:
 * price bands/circuit breakers, short-sale availability, and margin requirements. The
 * implemented rules and their per-preset toggles are documented in {@code docs/market-rules.md}.
 */
public enum RealismPreset {

    /** Forgiving: no circuit breakers, unrestricted shorting, low margin. */
    ARCADE(false, 0.20, 0L, true, 0.25, 0.125),

    /** Default: 10% bands with a 50-tick halt, shortable-only shorting, 2x margin. */
    REALISTIC(true, 0.10, 50L, false, 0.50, 0.25),

    /** Tight: 5% bands with a 100-tick halt, shortable-only shorting, tighter maintenance. */
    HARDCORE(true, 0.05, 100L, false, 0.50, 0.30);

    private final boolean priceBandsEnabled;
    private final double priceBandFraction;
    private final long haltDurationTicks;
    private final boolean shortingUnrestricted;
    private final double initialMarginFraction;
    private final double maintenanceMarginFraction;

    RealismPreset(
            boolean priceBandsEnabled,
            double priceBandFraction,
            long haltDurationTicks,
            boolean shortingUnrestricted,
            double initialMarginFraction,
            double maintenanceMarginFraction) {
        this.priceBandsEnabled = priceBandsEnabled;
        this.priceBandFraction = priceBandFraction;
        this.haltDurationTicks = haltDurationTicks;
        this.shortingUnrestricted = shortingUnrestricted;
        this.initialMarginFraction = initialMarginFraction;
        this.maintenanceMarginFraction = maintenanceMarginFraction;
    }

    public boolean priceBandsEnabled() {
        return priceBandsEnabled;
    }

    public double priceBandFraction() {
        return priceBandFraction;
    }

    public long haltDurationTicks() {
        return haltDurationTicks;
    }

    public boolean shortingUnrestricted() {
        return shortingUnrestricted;
    }

    public double initialMarginFraction() {
        return initialMarginFraction;
    }

    public double maintenanceMarginFraction() {
        return maintenanceMarginFraction;
    }

    /** A breaker for this preset, or null when the preset disables price bands. */
    public CircuitBreaker newCircuitBreaker(double referencePrice) {
        return priceBandsEnabled ? new CircuitBreaker(referencePrice, priceBandFraction, haltDurationTicks) : null;
    }

    /** A short-sale policy that honours this preset's shorting stance for the instrument. */
    public ShortSalePolicy newShortSalePolicy(boolean instrumentShortable) {
        return new ShortSalePolicy(shortingUnrestricted || instrumentShortable);
    }
}
