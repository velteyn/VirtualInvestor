package com.virtualinvestor.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import org.junit.jupiter.api.Test;

class OrderValidatorTest {

    private final MarketRules rules = new MarketRules(0.01, 10L, 10L);

    @Test
    void validLimitOrderPasses() {
        assertTrue(OrderValidator.validate(rules, OrderSide.BUY, OrderType.LIMIT, 100.05, 20).valid());
    }

    @Test
    void offTickPriceRejected() {
        ValidationResult result = OrderValidator.validate(rules, OrderSide.BUY, OrderType.LIMIT, 100.055, 20);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("tick"));
    }

    @Test
    void marketOrderIgnoresPriceTick() {
        assertTrue(OrderValidator.validate(rules, OrderSide.SELL, OrderType.MARKET, 100.0055, 20).valid());
    }

    @Test
    void quantityBelowGridRejected() {
        ValidationResult result = OrderValidator.validate(rules, OrderSide.BUY, OrderType.LIMIT, 100.00, 15);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("multiple"));
    }

    @Test
    void quantityBelowMinimumRejected() {
        MarketRules largeMinimum = new MarketRules(0.01, 1L, 100L);
        ValidationResult result = OrderValidator.validate(largeMinimum, OrderSide.BUY, OrderType.LIMIT, 100.00, 50);
        assertFalse(result.valid());
        assertEquals("quantity below minimum 100", result.reason());
    }

    @Test
    void nonPositiveQuantityRejected() {
        assertFalse(OrderValidator.validate(rules, OrderSide.BUY, OrderType.MARKET, 100.0, 0).valid());
        assertFalse(OrderValidator.validate(rules, OrderSide.BUY, OrderType.MARKET, 100.0, -10).valid());
    }
}
