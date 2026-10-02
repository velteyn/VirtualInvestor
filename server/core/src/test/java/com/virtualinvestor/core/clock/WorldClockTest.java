package com.virtualinvestor.core.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldClockTest {

    @Test
    void advancesNStepsDeterministically() {
        WorldClock clock = new WorldClock(100L, () -> 0L);
        List<Long> ticks = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        clock.addListener((tick, logicalMillis) -> {
            ticks.add(tick);
            times.add(logicalMillis);
        });

        clock.start();
        int taken = clock.step(100);

        assertEquals(100, taken);
        assertEquals(100L, clock.tick());
        assertEquals(10_000L, clock.logicalMillis());
        assertEquals(100, ticks.size());
        assertEquals(1L, ticks.get(0));
        assertEquals(100L, ticks.get(99));
        assertEquals(100L, times.get(0));
        assertEquals(10_000L, times.get(99));
    }

    @Test
    void sameStepCountProducesSameTrace() {
        List<Long> first = runTrace();
        List<Long> second = runTrace();
        assertEquals(first, second);
    }

    private static List<Long> runTrace() {
        WorldClock clock = new WorldClock(100L, () -> 0L);
        List<Long> trace = new ArrayList<>();
        clock.addListener((tick, logicalMillis) -> trace.add(logicalMillis));
        clock.start();
        clock.step(50);
        return trace;
    }

    @Test
    void doesNotStepWhenStoppedOrPaused() {
        WorldClock clock = new WorldClock(100L, () -> 0L);

        assertFalse(clock.step(), "stopped clock must not advance");

        clock.start();
        assertTrue(clock.step());
        assertEquals(1L, clock.tick());

        clock.pause();
        assertFalse(clock.step(), "paused clock must not advance");
        assertEquals(1L, clock.tick());
        assertEquals(WorldClock.State.PAUSED, clock.state());

        clock.resume();
        assertTrue(clock.step());
        assertEquals(2L, clock.tick());

        clock.stop();
        assertFalse(clock.step(), "stopped clock must not advance");
        assertEquals(2L, clock.tick());
    }

    @Test
    void rejectsNonPositiveStep() {
        assertThrows(IllegalArgumentException.class, () -> new WorldClock(0L, () -> 0L));
    }
}
