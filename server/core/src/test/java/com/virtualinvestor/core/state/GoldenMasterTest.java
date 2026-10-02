package com.virtualinvestor.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class GoldenMasterTest {

    @Test
    void fixedSeedProducesSameHashOnRerun() {
        GoldenMasterHarness.Result first = GoldenMasterHarness.run(42L, 1000);
        GoldenMasterHarness.Result second = GoldenMasterHarness.run(42L, 1000);

        assertEquals(1000L, first.ticks());
        assertEquals(first.hash(), second.hash());
        assertEquals(first.events(), second.events());
    }

    @Test
    void differentSeedsProduceDifferentHashes() {
        assertNotEquals(
                GoldenMasterHarness.run(1L, 200).hash(),
                GoldenMasterHarness.run(2L, 200).hash());
    }
}
