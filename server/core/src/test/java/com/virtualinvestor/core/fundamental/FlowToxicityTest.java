package com.virtualinvestor.core.fundamental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowToxicityTest {

    private static FlowToxicity toxicity() {
        return new FlowToxicity(1000.0, 1.0, 0.97, Map.of("ACME", 1_000_000.0));
    }

    @Test
    void calmBookScoresZero() {
        assertEquals(0.0, toxicity().toxicity("ACME"), 1e-9);
        assertEquals(0.0, toxicity().toxicity("UNKNOWN"), 1e-9);
    }

    @Test
    void oneSidedFlowRaisesTheScore() {
        FlowToxicity toxicity = toxicity();
        toxicity.observe("ACME", 100.0, 0.0);
        double afterBuy = toxicity.toxicity("ACME");

        assertTrue(afterBuy > 0.0);
        assertEquals(1000.0 * 100.0 / 1_000_000.0, afterBuy, 1e-9);

        toxicity.observe("ACME", -100.0, 0.0);
        assertTrue(toxicity.toxicity("ACME") > afterBuy, "direction is irrelevant, size matters");
    }

    @Test
    void calmDecaysTheScore() {
        FlowToxicity toxicity = toxicity();
        toxicity.observe("ACME", 100.0, 0.0);
        double peak = toxicity.toxicity("ACME");

        for (int i = 0; i < 10; i++) {
            toxicity.observe("ACME", 0.0, 0.0);
        }
        double decayed = toxicity.toxicity("ACME");

        assertTrue(decayed < peak);
        assertEquals(peak * Math.pow(0.97, 10), decayed, 1e-9);
    }

    @Test
    void volatilityAloneRaisesTheScore() {
        FlowToxicity toxicity = toxicity();
        toxicity.observe("ACME", 0.0, 0.5);

        assertEquals(0.5, toxicity.toxicity("ACME"), 1e-9);
    }
}
