package com.virtualinvestor.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import org.junit.jupiter.api.Test;

class RealismPresetTest {

    @Test
    void arcadeDisablesBandsAndUnrestrictsShorting() {
        assertFalse(RealismPreset.ARCADE.priceBandsEnabled());
        assertNull(RealismPreset.ARCADE.newCircuitBreaker(100.0));
        ShortSalePolicy policy = RealismPreset.ARCADE.newShortSalePolicy(false);
        assertTrue(policy.check(OrderSide.SELL, 0L, 10L).valid());
    }

    @Test
    void realisticEnablesBandsAndRequiresShortable() {
        assertTrue(RealismPreset.REALISTIC.priceBandsEnabled());
        CircuitBreaker breaker = RealismPreset.REALISTIC.newCircuitBreaker(100.0);
        assertNotNull(breaker);
        assertTrue(breaker.onPrice(111.0, 0L));

        assertFalse(RealismPreset.REALISTIC.newShortSalePolicy(false).check(OrderSide.SELL, 0L, 10L).valid());
        assertTrue(RealismPreset.REALISTIC.newShortSalePolicy(true).check(OrderSide.SELL, 0L, 10L).valid());
    }

    @Test
    void hardcoreHasTighterBandsThanRealistic() {
        assertTrue(RealismPreset.HARDCORE.priceBandFraction() < RealismPreset.REALISTIC.priceBandFraction());
        assertTrue(RealismPreset.HARDCORE.haltDurationTicks() >= RealismPreset.REALISTIC.haltDurationTicks());
    }
}
