package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReliabilityTierTest {

    @Test
    void rollSplitsWirePatronSpy() {
        assertEquals(ReliabilityTier.WIRE, ReliabilityTier.roll(0.0));
        assertEquals(ReliabilityTier.WIRE, ReliabilityTier.roll(0.599));
        assertEquals(ReliabilityTier.PATRON, ReliabilityTier.roll(0.6));
        assertEquals(ReliabilityTier.PATRON, ReliabilityTier.roll(0.899));
        assertEquals(ReliabilityTier.SPY, ReliabilityTier.roll(0.9));
        assertEquals(ReliabilityTier.SPY, ReliabilityTier.roll(0.999));
    }

    @Test
    void rollRejectsOutsideUnitInterval() {
        assertThrows(IllegalArgumentException.class, () -> ReliabilityTier.roll(-0.1));
        assertThrows(IllegalArgumentException.class, () -> ReliabilityTier.roll(1.0));
    }

    @Test
    void spyConfirmsLeastOften() {
        assertTrue(ReliabilityTier.SPY.confirmBias() < ReliabilityTier.PATRON.confirmBias());
        assertTrue(ReliabilityTier.PATRON.confirmBias() < ReliabilityTier.WIRE.confirmBias());
    }

    @Test
    void generatorDealsRoughlySixtyThirtyTen() {
        Map<ReliabilityTier, Integer> counts = new EnumMap<>(ReliabilityTier.class);
        java.util.Random random = new java.util.Random(42L);
        int total = 20_000;
        for (int i = 0; i < total; i++) {
            ReliabilityTier tier = ReliabilityTier.roll(random.nextDouble());
            counts.merge(tier, 1, Integer::sum);
        }
        double wire = counts.getOrDefault(ReliabilityTier.WIRE, 0) / (double) total;
        double patron = counts.getOrDefault(ReliabilityTier.PATRON, 0) / (double) total;
        double spy = counts.getOrDefault(ReliabilityTier.SPY, 0) / (double) total;
        assertTrue(Math.abs(wire - 0.6) < 0.02, "wire ~60%, was " + wire);
        assertTrue(Math.abs(patron - 0.3) < 0.02, "patron ~30%, was " + patron);
        assertTrue(Math.abs(spy - 0.1) < 0.02, "spy ~10%, was " + spy);
    }
}
