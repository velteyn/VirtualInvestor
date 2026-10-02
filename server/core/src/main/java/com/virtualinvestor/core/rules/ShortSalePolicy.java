package com.virtualinvestor.core.rules;

import com.virtualinvestor.core.domain.OrderSide;

/**
 * Whether an instrument may be sold short. A sell that would leave a participant net short
 * is refused for a non-shortable instrument; sells that only reduce a long are always allowed.
 */
public final class ShortSalePolicy {

    private final boolean shortable;

    public ShortSalePolicy(boolean shortable) {
        this.shortable = shortable;
    }

    public boolean shortable() {
        return shortable;
    }

    public ValidationResult check(OrderSide side, long currentNetQty, long orderQuantity) {
        if (side != OrderSide.SELL) {
            return ValidationResult.ok();
        }
        long resultingNetQty = currentNetQty - orderQuantity;
        if (resultingNetQty < 0L && !shortable) {
            return ValidationResult.reject("short selling is not available for this instrument");
        }
        return ValidationResult.ok();
    }
}
