package com.virtualinvestor.core.auction;

import com.virtualinvestor.core.domain.OrderSide;

/**
 * An order submitted into an auction. A market order ({@code market == true}) is willing to
 * trade at any clearing price; a limit order is willing only at its price or better.
 */
public record AuctionOrder(
        String id,
        String participantId,
        OrderSide side,
        long quantity,
        double limitPrice,
        boolean market) {

    public boolean willingAt(double price) {
        if (market) {
            return true;
        }
        return side == OrderSide.BUY ? limitPrice >= price : limitPrice <= price;
    }

    public double candidatePrice() {
        return limitPrice;
    }
}
