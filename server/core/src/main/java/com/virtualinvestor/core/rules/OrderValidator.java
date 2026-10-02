package com.virtualinvestor.core.rules;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;

/**
 * Pre-trade validation that blocks orders the venue would reject, so the client gets an
 * explicit reason rather than a silent failure. Price constraints apply only to orders that
 * carry a price (LIMIT and STOP_LIMIT); MARKET and STOP orders ignore the price fields.
 */
public final class OrderValidator {

    private static final double TICK_EPSILON = 1e-6;

    private OrderValidator() {
    }

    public static ValidationResult validate(
            MarketRules rules, OrderSide side, OrderType type, double price, long quantity) {
        if (side == null) {
            return ValidationResult.reject("side is required");
        }
        if (type == null) {
            return ValidationResult.reject("type is required");
        }
        if (quantity <= 0L) {
            return ValidationResult.reject("quantity must be positive");
        }
        if (quantity < rules.minimumQuantity()) {
            return ValidationResult.reject("quantity below minimum " + rules.minimumQuantity());
        }
        if (quantity % rules.quantityGrid() != 0L) {
            return ValidationResult.reject("quantity must be a multiple of " + rules.quantityGrid());
        }
        if ((type == OrderType.LIMIT || type == OrderType.STOP_LIMIT) && !isOnTick(price, rules.tickSize())) {
            return ValidationResult.reject("price must be a multiple of the tick " + rules.tickSize());
        }
        return ValidationResult.ok();
    }

    public static boolean isOnTick(double price, double tickSize) {
        if (!Double.isFinite(price) || price <= 0.0) {
            return false;
        }
        double ticks = price / tickSize;
        return Math.abs(ticks - Math.rint(ticks)) < TICK_EPSILON;
    }
}
