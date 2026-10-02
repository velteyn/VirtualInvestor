package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;

/**
 * VULTURE: pounces on a headline, trading in the direction of the news magnitude once it is
 * large enough to matter.
 */
public final class NewsHunterStrategy implements BotStrategy {

    private final double minimumMagnitude;
    private final long orderQuantity;

    public NewsHunterStrategy(double minimumMagnitude, long orderQuantity) {
        if (minimumMagnitude < 0.0) {
            throw new IllegalArgumentException("minimumMagnitude must not be negative");
        }
        if (orderQuantity <= 0L) {
            throw new IllegalArgumentException("orderQuantity must be positive");
        }
        this.minimumMagnitude = minimumMagnitude;
        this.orderQuantity = orderQuantity;
    }

    @Override
    public List<OrderIntent> decide(BotContext context) {
        NewsSignal news = context.news();
        if (news == null || Math.abs(news.signedMagnitude()) < minimumMagnitude) {
            return List.of();
        }
        OrderSide side = news.signedMagnitude() > 0.0 ? OrderSide.BUY : OrderSide.SELL;
        return List.of(OrderIntent.market(side, orderQuantity));
    }
}
