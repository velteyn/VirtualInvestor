package com.virtualinvestor.core.bot;

/**
 * A laddered two-sided quote: the prices and sizes a market maker is willing to
 * trade at. Level 0 is the touch; deeper levels step outward and grow, so large
 * orders visibly walk the book (realistic-liquidity 1.1).
 */
public record Quote(java.util.List<Level> bidLevels, java.util.List<Level> askLevels) {

    /** One ladder rung. */
    public record Level(double price, long size) {
    }

    public Quote {
        if (bidLevels == null || bidLevels.isEmpty() || askLevels == null || askLevels.isEmpty()) {
            throw new IllegalArgumentException("a quote needs at least one level per side");
        }
        bidLevels = java.util.List.copyOf(bidLevels);
        askLevels = java.util.List.copyOf(askLevels);
    }

    public double bidPrice() {
        return bidLevels.get(0).price();
    }

    public double askPrice() {
        return askLevels.get(0).price();
    }

    public long bidSize() {
        return bidLevels.get(0).size();
    }

    public long askSize() {
        return askLevels.get(0).size();
    }

    public double mid() {
        return (bidPrice() + askPrice()) / 2.0;
    }

    public double spread() {
        return askPrice() - bidPrice();
    }
}
