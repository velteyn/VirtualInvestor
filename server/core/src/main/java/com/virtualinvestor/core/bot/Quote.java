package com.virtualinvestor.core.bot;

/** A two-sided quote: the prices and sizes a market maker is willing to trade at. */
public record Quote(double bidPrice, double askPrice, long bidSize, long askSize) {

    public double mid() {
        return (bidPrice + askPrice) / 2.0;
    }

    public double spread() {
        return askPrice - bidPrice;
    }
}
