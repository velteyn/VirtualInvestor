package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;

/** An order entering the matching engine. */
public record IncomingOrder(
        String id,
        String participantId,
        String symbol,
        OrderSide side,
        OrderType type,
        long quantity,
        double limitPrice) {
}
