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
    private final int levelCount;
    private final double sizeGrowth;
    private final double toxicitySensitivity;
    private final double toxicityTolerance;

    public MarketMakerStrategy(
            double baseHalfSpread, double volatilitySensitivity, double inventorySkewPerUnit, long baseSize) {
        this(baseHalfSpread, volatilitySensitivity, inventorySkewPerUnit, baseSize, 1, 2.0);
    }

    /**
     * @param levelCount  ladder depth per side (1 preserves the classic single-level quote)
     * @param sizeGrowth  each deeper level's size multiplier (small at the touch, larger behind)
     */
    public MarketMakerStrategy(double baseHalfSpread, double volatilitySensitivity, double inventorySkewPerUnit,
            long baseSize, int levelCount, double sizeGrowth) {
        this(baseHalfSpread, volatilitySensitivity, inventorySkewPerUnit, baseSize, levelCount, sizeGrowth,
                0.0, Double.POSITIVE_INFINITY);
    }

    /**
     * @param toxicitySensitivity how fast toxicity shrinks size and widens spread (0 ignores it)
     * @param toxicityTolerance   toxicity at or above which the maker withdraws fully
     */
    public MarketMakerStrategy(double baseHalfSpread, double volatilitySensitivity, double inventorySkewPerUnit,
            long baseSize, int levelCount, double sizeGrowth, double toxicitySensitivity,
            double toxicityTolerance) {
        if (baseHalfSpread <= 0.0) {
            throw new IllegalArgumentException("baseHalfSpread must be positive");
        }
        if (volatilitySensitivity < 0.0 || inventorySkewPerUnit < 0.0) {
            throw new IllegalArgumentException("sensitivities must not be negative");
        }
        if (baseSize <= 0L) {
            throw new IllegalArgumentException("baseSize must be positive");
        }
        if (levelCount <= 0) {
            throw new IllegalArgumentException("levelCount must be positive");
        }
        if (sizeGrowth < 1.0) {
            throw new IllegalArgumentException("sizeGrowth must be at least 1");
        }
        if (toxicitySensitivity < 0.0) {
            throw new IllegalArgumentException("toxicitySensitivity must not be negative");
        }
        if (!(toxicityTolerance > 0.0)) {
            throw new IllegalArgumentException("toxicityTolerance must be positive");
        }
        this.baseHalfSpread = baseHalfSpread;
        this.volatilitySensitivity = volatilitySensitivity;
        this.inventorySkewPerUnit = inventorySkewPerUnit;
        this.baseSize = baseSize;
        this.levelCount = levelCount;
        this.sizeGrowth = sizeGrowth;
        this.toxicitySensitivity = toxicitySensitivity;
        this.toxicityTolerance = toxicityTolerance;
    }

    /** Base touch size, before equity scaling (used to scale deeper levels). */
    public long baseSize() {
        return baseSize;
    }

    /**
     * @param fundamental latest fundamental value
     * @param volatility  volatility state (>= 0; higher means a wider spread)
     * @param inventory   signed position the maker currently holds
     */
    public Quote quote(double fundamental, double volatility, long inventory) {
        return quote(fundamental, volatility, inventory, 0.0);
    }

    /**
     * @param toxicity order-flow toxicity (>= 0): shrinks sizes, widens the spread,
     *                 and empties the quote at or above tolerance (realistic-liquidity 2.2)
     */
    public Quote quote(double fundamental, double volatility, long inventory, double toxicity) {
        double stress = Math.max(0.0, toxicity);
        double halfSpread = baseHalfSpread * (1.0 + volatilitySensitivity * Math.max(0.0, volatility))
                * (1.0 + toxicitySensitivity * stress);
        double mid = fundamental - inventorySkewPerUnit * inventory;
        double sizeScale = stress >= toxicityTolerance ? 0.0 : Math.max(0.0, 1.0 - toxicitySensitivity * stress);
        java.util.List<Quote.Level> bids = new java.util.ArrayList<>(levelCount);
        java.util.List<Quote.Level> asks = new java.util.ArrayList<>(levelCount);
        long size = baseSize;
        for (int level = 0; level < levelCount; level++) {
            // Each rung steps one half-spread further out and grows geometrically.
            long scaled = (long) Math.floor(size * sizeScale);
            bids.add(new Quote.Level(mid - halfSpread * (level + 1), scaled));
            asks.add(new Quote.Level(mid + halfSpread * (level + 1), scaled));
            size = Math.max(size + 1L, Math.round(size * sizeGrowth));
        }
        return new Quote(bids, asks);
    }
}
