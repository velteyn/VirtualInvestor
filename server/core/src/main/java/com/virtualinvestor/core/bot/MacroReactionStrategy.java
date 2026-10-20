package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.news.ReliabilityTier;
import java.util.List;

/**
 * ATLAS/MERIDIAN: institutions react to macro news with size. Unlike the retail news-hunter
 * they only act on MACRO-scope news and trade a larger quantity. They also only trust the
 * wire: patron and spy rumors move no institutional size until the wire confirms them.
 * Dispersion dulls the perceived magnitude exactly like the hunter's.
 */
public final class MacroReactionStrategy implements BotStrategy {

    private final double minimumMagnitude;
    private final long orderQuantity;
    private final double dispersion;

    public MacroReactionStrategy(double minimumMagnitude, long orderQuantity, double dispersion) {
        if (minimumMagnitude < 0.0) {
            throw new IllegalArgumentException("minimumMagnitude must not be negative");
        }
        if (orderQuantity <= 0L) {
            throw new IllegalArgumentException("orderQuantity must be positive");
        }
        if (dispersion < 0.0 || dispersion > 100.0) {
            throw new IllegalArgumentException("dispersion must be within [0, 100]");
        }
        this.minimumMagnitude = minimumMagnitude;
        this.orderQuantity = orderQuantity;
        this.dispersion = dispersion;
    }

    @Override
    public List<OrderIntent> decide(BotContext context) {
        NewsSignal news = context.news();
        if (news == null || news.scope() != NewsScope.MACRO || news.tier() != ReliabilityTier.WIRE) {
            return List.of();
        }
        double perceived = Math.abs(news.signedMagnitude()) * (1.0 - 0.01 * dispersion);
        if (perceived < minimumMagnitude) {
            return List.of();
        }
        OrderSide side = news.signedMagnitude() > 0.0 ? OrderSide.BUY : OrderSide.SELL;
        return List.of(OrderIntent.market(side, orderQuantity));
    }
}
