package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;

/**
 * TIDE: buys when recent price action is up and sells when it is down, amplifying moves.
 */
public final class MomentumStrategy implements BotStrategy {

    private final double threshold;
    private final long orderQuantity;

    public MomentumStrategy(double threshold, long orderQuantity) {
        if (threshold <= 0.0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        if (orderQuantity <= 0L) {
            throw new IllegalArgumentException("orderQuantity must be positive");
        }
        this.threshold = threshold;
        this.orderQuantity = orderQuantity;
    }

    @Override
    public List<OrderIntent> decide(BotContext context) {
        if (context.priceChange() > threshold) {
            return List.of(OrderIntent.market(OrderSide.BUY, orderQuantity));
        }
        if (context.priceChange() < -threshold) {
            return List.of(OrderIntent.market(OrderSide.SELL, orderQuantity));
        }
        return List.of();
    }
}
