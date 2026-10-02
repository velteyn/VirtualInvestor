package com.virtualinvestor.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import org.junit.jupiter.api.Test;

class ShortSalePolicyTest {

    @Test
    void nonShortableInstrumentRefusesSellThatOpensAShort() {
        ShortSalePolicy policy = new ShortSalePolicy(false);
        ValidationResult result = policy.check(OrderSide.SELL, 0L, 10L);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("short"));
    }

    @Test
    void nonShortableInstrumentAllowsSellingExistingLong() {
        ShortSalePolicy policy = new ShortSalePolicy(false);
        assertTrue(policy.check(OrderSide.SELL, 10L, 5L).valid());
        assertTrue(policy.check(OrderSide.SELL, 10L, 10L).valid());
    }

    @Test
    void shortableInstrumentAllowsShorting() {
        ShortSalePolicy policy = new ShortSalePolicy(true);
        assertTrue(policy.check(OrderSide.SELL, 0L, 10L).valid());
    }

    @Test
    void buyOrdersAreAlwaysAllowed() {
        ShortSalePolicy policy = new ShortSalePolicy(false);
        assertTrue(policy.check(OrderSide.BUY, 0L, 10L).valid());
    }
}
