package com.virtualinvestor.core.auction;

import com.virtualinvestor.core.domain.OrderSide;

/** The quantity an auction order was allocated at the clearing price. */
public record AuctionFill(
        String orderId,
        String participantId,
        OrderSide side,
        long quantity,
        double price) {
}
