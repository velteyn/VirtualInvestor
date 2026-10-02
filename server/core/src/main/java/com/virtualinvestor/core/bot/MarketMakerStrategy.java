package com.virtualinvestor.core.bot;

/**
 * Quotes two-sided prices around an instrument's fundamental value. The half-spread widens
 * with the volatility state (fear widens markets), and the mid is skewed against inventory so
 * the maker quotes more aggressively to shed a large position and more passively to rebuild a
 * flat one. This is the liquidity provider behind MARK and the institutions.
 */
public final class MarketMakerStrategy {

    private final double baseHalfSpread;
    private final double volatilitySensitivity;
    private final double inventorySkewPerUnit;
    private final long baseSize;

    public MarketMakerStrategy(
            double baseHalfSpread, double volatilitySensitivity, double inventorySkewPerUnit, long baseSize) {
        if (baseHalfSpread <= 0.0) {
            throw new IllegalArgumentException("baseHalfSpread must be positive");
        }
        if (volatilitySensitivity < 0.0 || inventorySkewPerUnit < 0.0) {
            throw new IllegalArgumentException("sensitivities must not be negative");
        }
        if (baseSize <= 0L) {
            throw new IllegalArgumentException("baseSize must be positive");
        }
        this.baseHalfSpread = baseHalfSpread;
        this.volatilitySensitivity = volatilitySensitivity;
        this.inventorySkewPerUnit = inventorySkewPerUnit;
        this.baseSize = baseSize;
    }

    /**
     * @param fundamental latest fundamental value
     * @param volatility  volatility state (>= 0; higher means a wider spread)
     * @param inventory   signed position the maker currently holds
     */
    public Quote quote(double fundamental, double volatility, long inventory) {
        double halfSpread = baseHalfSpread * (1.0 + volatilitySensitivity * Math.max(0.0, volatility));
        double mid = fundamental - inventorySkewPerUnit * inventory;
        return new Quote(mid - halfSpread, mid + halfSpread, baseSize, baseSize);
    }
}
