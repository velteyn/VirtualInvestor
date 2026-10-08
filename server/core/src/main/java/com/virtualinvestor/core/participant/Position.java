package com.virtualinvestor.core.participant;

/**
 * A net position in one instrument. {@code netQty} is signed (positive long, negative short);
 * {@code avgPrice} is the average entry price of the open quantity; {@code realized} is the
 * P&L already booked on this symbol (portfolio-tab 1.2), which survives partial closes.
 */
public record Position(String ticker, long netQty, double avgPrice, double realized) {

    public static Position flat(String ticker) {
        return new Position(ticker, 0L, 0.0, 0.0);
    }

    public boolean isFlat() {
        return netQty == 0L;
    }

    public double marketValue(double markPrice) {
        return netQty * markPrice;
    }

    public double unrealized(double markPrice) {
        return (markPrice - avgPrice) * netQty;
    }
}
