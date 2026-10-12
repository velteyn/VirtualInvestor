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

    /** Series notional per order is bounded by this fraction of current equity. */
    public static final double SERIES_MAX_NOTIONAL_FRACTION = 1.0;

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

    /**
     * Size a derivatives order against equity and margin: futures notional is bounded
     * by {@link #SERIES_MAX_NOTIONAL_FRACTION} of equity with locked margin fitting
     * free buying power; long options must fit buying power in premium; short options
     * must cover strike-based exposure at the series initial margin.
     */
    public static ValidationResult checkSeriesSize(
            com.virtualinvestor.core.instrument.SeriesSymbols.SeriesTerms terms,
            OrderSide side, long quantity, double price,
            double equity, double buyingPower, double initialMarginFraction) {
        if (quantity <= 0L) {
            return ValidationResult.reject("quantity must be positive");
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            return ValidationResult.reject("price must be positive");
        }
        switch (terms.kind()) {
            case FUTURE -> {
                double notional = price * terms.multiplier() * quantity;
                if (notional > equity * SERIES_MAX_NOTIONAL_FRACTION) {
                    return ValidationResult.reject("futures notional exceeds equity");
                }
                double margin = initialMarginFraction * notional;
                if (margin > buyingPower) {
                    return ValidationResult.reject("insufficient margin for futures order");
                }
            }
            case CALL, PUT -> {
                if (side == OrderSide.BUY) {
                    if (price * quantity > buyingPower) {
                        return ValidationResult.reject("insufficient buying power for option premium");
                    }
                } else {
                    double exposure = terms.strike() * terms.multiplier() * quantity;
                    if (initialMarginFraction * exposure > buyingPower) {
                        return ValidationResult.reject("insufficient margin for short option");
                    }
                }
            }
        }
        return ValidationResult.ok();
    }
}
