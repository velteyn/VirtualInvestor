package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

class LaunchConfigTest {

    @Test
    void validConfigAccepted() {
        LaunchConfig config = new LaunchConfig(100_000.0, 30L, RealismPreset.REALISTIC);
        assertEquals(100_000.0, config.startingCapital(), 0.0);
        assertEquals(30L, config.durationMinutes());
        assertEquals(18_000L, config.durationTicks(100L));
    }

    @Test
    void capitalOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchConfig(9_999.0, 30L, RealismPreset.REALISTIC));
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchConfig(5_000_001.0, 30L, RealismPreset.REALISTIC));
    }

    @Test
    void durationOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchConfig(100_000.0, 14L, RealismPreset.REALISTIC));
        assertThrows(IllegalArgumentException.class,
                () -> new LaunchConfig(100_000.0, 241L, RealismPreset.REALISTIC));
    }

    @Test
    void presetRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LaunchConfig(100_000.0, 30L, null));
    }

    @Test
    void boundaryValuesAccepted() {
        new LaunchConfig(LaunchConfig.MIN_CAPITAL, LaunchConfig.MIN_MINUTES, RealismPreset.ARCADE);
        new LaunchConfig(LaunchConfig.MAX_CAPITAL, LaunchConfig.MAX_MINUTES, RealismPreset.HARDCORE);
    }
}
