package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;

/**
 * An untriggered stop order. It becomes a live {@link IncomingOrder} only when the last
 * traded price crosses {@code stopPrice}: a STOP becomes a market order, a STOP_LIMIT
 * becomes a limit order at {@code limitPrice}.
 */
public record StopOrder(
        String id,
        String participantId,
        String symbol,
        OrderSide side,
        OrderType type,
        long quantity,
        double stopPrice,
        double limitPrice) {

    public StopOrder {
        if (type != OrderType.STOP && type != OrderType.STOP_LIMIT) {
            throw new IllegalArgumentException("StopOrder type must be STOP or STOP_LIMIT, was " + type);
        }
    }
}
