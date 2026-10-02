package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

class RoundLifecycleTest {

    private static RoundLifecycle lifecycle() {
        return new RoundLifecycle("r1", 42L, new LaunchConfig(100_000.0, 30L, RealismPreset.REALISTIC));
    }

    @Test
    void happyPathTransitions() {
        RoundLifecycle round = lifecycle();
        assertEquals(RoundState.CREATED, round.state());

        round.transitionTo(RoundState.WARMUP);
        round.transitionTo(RoundState.RUNNING);
        round.transitionTo(RoundState.CLOSING);
        round.transitionTo(RoundState.SETTLED);

        assertEquals(RoundState.SETTLED, round.state());
    }

    @Test
    void pauseAndResumeAreAllowedFromRunning() {
        RoundLifecycle round = lifecycle();
        round.transitionTo(RoundState.WARMUP);
        round.transitionTo(RoundState.RUNNING);

        round.transitionTo(RoundState.PAUSED);
        assertEquals(RoundState.PAUSED, round.state());
        round.transitionTo(RoundState.RUNNING);
        assertEquals(RoundState.RUNNING, round.state());
    }

    @Test
    void illegalTransitionsAreRejected() {
        RoundLifecycle round = lifecycle();
        assertThrows(IllegalStateException.class, () -> round.transitionTo(RoundState.RUNNING));
        assertFalse(RoundLifecycle.allowed(RoundState.CREATED, RoundState.RUNNING));
        assertFalse(RoundLifecycle.allowed(RoundState.SETTLED, RoundState.RUNNING));

        round.transitionTo(RoundState.WARMUP);
        assertThrows(IllegalStateException.class, () -> round.transitionTo(RoundState.WARMUP));
    }

    @Test
    void signatureCapturesSeedAndConfig() {
        RoundLifecycle round = lifecycle();
        assertTrue(round.signature().contains("seed=42"));
        assertTrue(round.signature().contains("capital=100000.0"));
        assertTrue(round.signature().contains("preset=REALISTIC"));
    }
}
