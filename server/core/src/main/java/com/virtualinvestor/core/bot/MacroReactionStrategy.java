package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;

/**
 * ATLAS/MERIDIAN: institutions react to macro news with size. Unlike the retail news-hunter
 * they only act on MACRO-scope news and trade a larger quantity.
 */
public final class MacroReactionStrategy implements BotStrategy {

    private final double minimumMagnitude;
    private final long orderQuantity;

    public MacroReactionStrategy(double minimumMagnitude, long orderQuantity) {
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
        if (news == null || news.scope() != NewsScope.MACRO || Math.abs(news.signedMagnitude()) < minimumMagnitude) {
            return List.of();
        }
        OrderSide side = news.signedMagnitude() > 0.0 ? OrderSide.BUY : OrderSide.SELL;
        return List.of(OrderIntent.market(side, orderQuantity));
    }
}
