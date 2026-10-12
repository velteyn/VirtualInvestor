package com.virtualinvestor.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class OptionPricingTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void sameInputsGiveSamePremium() {
        assertEquals(
                OptionPricing.call(640.0, 640.0, 0.002, 0.45),
                OptionPricing.call(640.0, 640.0, 0.002, 0.45),
                TOLERANCE);
    }

    @Test
    void putCallParityWithZeroRate() {
        double spot = 640.0;
        double strike = 620.0;
        double call = OptionPricing.call(spot, strike, 0.002, 0.45);
        double put = OptionPricing.put(spot, strike, 0.002, 0.45);
        assertEquals(spot - strike, call - put, 1e-6);
    }

    @Test
    void atExpiryPremiumIsIntrinsic() {
        assertEquals(20.0, OptionPricing.call(640.0, 620.0, 0.0, 0.45), TOLERANCE);
        assertEquals(0.0, OptionPricing.call(600.0, 620.0, 0.0, 0.45), TOLERANCE);
        assertEquals(20.0, OptionPricing.put(600.0, 620.0, 0.0, 0.45), TOLERANCE);
    }

    @Test
    void premiumIsSane() {
        double call = OptionPricing.call(640.0, 640.0, 0.002, 0.45);
        assertTrue(call > 0.0 && call < 640.0, () -> "atm call premium out of range: " + call);
        assertTrue(OptionPricing.call(640.0, 700.0, 0.002, 0.45) < call);
        assertTrue(OptionPricing.put(640.0, 580.0, 0.002, 0.45) < OptionPricing.put(640.0, 640.0, 0.002, 0.45));
    }

    @Test
    void invalidInputsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OptionPricing.call(-1.0, 640.0, 0.002, 0.45));
        assertThrows(IllegalArgumentException.class, () -> OptionPricing.put(640.0, 0.0, 0.002, 0.45));
        assertThrows(IllegalArgumentException.class, () -> OptionPricing.call(640.0, 640.0, -0.1, 0.45));
        assertThrows(IllegalArgumentException.class, () -> OptionPricing.call(640.0, 640.0, 0.002, -0.5));
    }

    @Test
    void optionMarkFallsBackToFormula() {
        assertEquals(12.5, SeriesMarks.optionMark(OptionalDouble.of(12.5), 11.0), TOLERANCE);
        assertEquals(11.0, SeriesMarks.optionMark(OptionalDouble.empty(), 11.0), TOLERANCE);
    }
}
