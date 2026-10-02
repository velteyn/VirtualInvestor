package com.virtualinvestor.core.rules;

/**
 * Price-band circuit breaker. When the last traded price moves beyond the configured band
 * from the reference, trading halts for a fixed number of ticks. After the halt the reference
 * resets to the triggering price, so the instrument trades again around the new level.
 */
public final class CircuitBreaker {

    private final double bandFraction;
    private final long haltDurationTicks;

    private double referencePrice;
    private long haltedUntilTick = Long.MIN_VALUE;
    private boolean halted = false;

    public CircuitBreaker(double referencePrice, double bandFraction, long haltDurationTicks) {
        if (referencePrice <= 0.0) {
            throw new IllegalArgumentException("referencePrice must be positive");
        }
        if (bandFraction <= 0.0 || bandFraction >= 1.0) {
            throw new IllegalArgumentException("bandFraction must be within (0, 1)");
        }
        if (haltDurationTicks <= 0L) {
            throw new IllegalArgumentException("haltDurationTicks must be positive");
        }
        this.referencePrice = referencePrice;
        this.bandFraction = bandFraction;
        this.haltDurationTicks = haltDurationTicks;
    }

    public double referencePrice() {
        return referencePrice;
    }

    public long haltedUntilTick() {
        return haltedUntilTick;
    }

    /** Observe a traded price; returns true if this price triggered a halt. */
    public boolean onPrice(double price, long tick) {
        if (isHalted(tick)) {
            return false;
        }
        double move = Math.abs(price / referencePrice - 1.0);
        if (move >= bandFraction) {
            halted = true;
            haltedUntilTick = tick + haltDurationTicks;
            referencePrice = price;
            return true;
        }
        return false;
    }

    public boolean isHalted(long tick) {
        if (!halted) {
            return false;
        }
        if (tick >= haltedUntilTick) {
            halted = false;
            return false;
        }
        return true;
    }
}
