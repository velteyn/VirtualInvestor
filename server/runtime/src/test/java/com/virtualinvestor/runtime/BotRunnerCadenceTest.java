package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Book-replenishment 1.1: rung periods fit the session, touch still leads. */
class BotRunnerCadenceTest {

    @Test
    void everyRungFiresAtLeastThricePerShortSession() {
        long session = 180L;
        for (int level = 0; level < 5; level++) {
            long period = BotRunner.rungPeriod(level, 50L, session);
            assertTrue(period <= session / 3L,
                    "rung " + level + " must fire ≥3x per session, period=" + period);
            assertTrue(period >= 50L, "nothing refreshes faster than the touch");
        }
        assertEquals(50L, BotRunner.rungPeriod(0, 50L, session), "the touch leads");
    }

    @Test
    void longSessionsKeepQuadraticLinger() {
        assertEquals(50L, BotRunner.rungPeriod(0, 50L, 100_000L));
        assertEquals(200L, BotRunner.rungPeriod(1, 50L, 100_000L));
        assertEquals(450L, BotRunner.rungPeriod(2, 50L, 100_000L));
    }

    @Test
    void legacyPathStaysPurelyQuadratic() {
        assertEquals(1_250L, BotRunner.rungPeriod(4, 50L, Long.MAX_VALUE));
    }
}
