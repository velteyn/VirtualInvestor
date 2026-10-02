package com.virtualinvestor.core.market;

/** One OHLCV bar of generated history. */
public record Bar(long timeSec, double open, double high, double low, double close, long volume) {
}
