package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;

/**
 * BLITZ: fades short-term moves, selling into a pop and buying a dip, harvesting small
 * mean-reverting edges around the spread.
 */
public final class ScalperStrategy implements BotStrategy {

    private final double threshold;
    private final long orderQuantity;

    public ScalperStrategy(double threshold, long orderQuantity) {
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
            return List.of(OrderIntent.market(OrderSide.SELL, orderQuantity));
        }
        if (context.priceChange() < -threshold) {
            return List.of(OrderIntent.market(OrderSide.BUY, orderQuantity));
        }
        return List.of();
    }
}
