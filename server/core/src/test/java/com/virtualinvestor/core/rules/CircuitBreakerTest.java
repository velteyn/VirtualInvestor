package com.virtualinvestor.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    @Test
    void withinBandTradesNormally() {
        CircuitBreaker breaker = new CircuitBreaker(100.0, 0.10, 50L);
        assertFalse(breaker.onPrice(105.0, 0L));
        assertFalse(breaker.isHalted(0L));
    }

    @Test
    void beyondBandHaltsThenResumes() {
        CircuitBreaker breaker = new CircuitBreaker(100.0, 0.10, 50L);

        assertTrue(breaker.onPrice(111.0, 10L), "11% move should halt");
        assertTrue(breaker.isHalted(20L));
        assertTrue(breaker.isHalted(59L));
        assertFalse(breaker.isHalted(60L), "halt expires at tick 60");

        assertEquals(111.0, breaker.referencePrice(), 0.0, "reference resets to the triggering price");
    }

    @Test
    void downBandHaltsToo() {
        CircuitBreaker breaker = new CircuitBreaker(100.0, 0.10, 30L);
        assertTrue(breaker.onPrice(89.0, 5L));
        assertTrue(breaker.isHalted(10L));
    }

    @Test
    void noRetriggerWhileHalted() {
        CircuitBreaker breaker = new CircuitBreaker(100.0, 0.10, 50L);
        assertTrue(breaker.onPrice(111.0, 0L));
        assertFalse(breaker.onPrice(112.0, 5L), "already halted, no new halt event");
    }
}
