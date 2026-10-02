package com.virtualinvestor.core.bot;

/**
 * Everything a strategy sees when deciding: the market state, its own inventory, and the
 * latest news signal (null when there is none this tick).
 */
public record BotContext(
        String symbol,
        double lastPrice,
        double fundamental,
        double priceChange,
        double volatility,
        long netPosition,
        double cash,
        NewsSignal news,
        long tick) {
}
