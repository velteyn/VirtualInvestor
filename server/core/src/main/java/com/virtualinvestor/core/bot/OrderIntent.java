package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;

/** An order a strategy wants to place this tick. */
public record OrderIntent(OrderSide side, OrderType type, long quantity, double price) {

    public static OrderIntent market(OrderSide side, long quantity) {
        return new OrderIntent(side, OrderType.MARKET, quantity, 0.0);
    }

    public static OrderIntent limit(OrderSide side, long quantity, double price) {
        return new OrderIntent(side, OrderType.LIMIT, quantity, price);
    }
}
