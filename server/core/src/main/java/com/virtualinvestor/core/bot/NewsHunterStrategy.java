package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.news.ReliabilityTier;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * VULTURE: pounces on a headline, trading in the direction of the news magnitude
 * once it is large enough to matter. Each hunter perceives the headline through
 * its own dispersion lens (WST98's stat-statted rivals, made replay-safe): the
 * perceived magnitude is {@code |signed| * (1 - 0.01 * dispersion)}, so a noisy
 * hunter sits out borderline headlines a precise one trades. Hunters read every
 * reliability tier — speed over skepticism.
 */
public final class NewsHunterStrategy implements BotStrategy {

    private final double minimumMagnitude;
    private final long orderQuantity;
    private final double dispersion;
    private final Set<ReliabilityTier> trustedTiers;

    public NewsHunterStrategy(double minimumMagnitude, long orderQuantity, double dispersion) {
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
        this.trustedTiers = EnumSet.allOf(ReliabilityTier.class);
    }

    @Override
    public List<OrderIntent> decide(BotContext context) {
        NewsSignal news = context.news();
        if (news == null || !trustedTiers.contains(news.tier())) {
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
