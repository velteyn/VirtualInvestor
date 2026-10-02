package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;

/**
 * LEDGER: buys when the price is below the fundamental value by the entry edge and sells when
 * it is above, pulling price back toward the fundamental.
 */
public final class ValueStrategy implements BotStrategy {

    private final double entryEdge;
    private final long orderQuantity;

    public ValueStrategy(double entryEdge, long orderQuantity) {
        if (entryEdge <= 0.0) {
            throw new IllegalArgumentException("entryEdge must be positive");
        }
        if (orderQuantity <= 0L) {
            throw new IllegalArgumentException("orderQuantity must be positive");
        }
        this.entryEdge = entryEdge;
        this.orderQuantity = orderQuantity;
    }

    @Override
    public List<OrderIntent> decide(BotContext context) {
        if (context.fundamental() <= 0.0) {
            return List.of();
        }
        if (context.lastPrice() < context.fundamental() * (1.0 - entryEdge)) {
            return List.of(OrderIntent.market(OrderSide.BUY, orderQuantity));
        }
        if (context.lastPrice() > context.fundamental() * (1.0 + entryEdge)) {
            return List.of(OrderIntent.market(OrderSide.SELL, orderQuantity));
        }
        return List.of();
    }
}
