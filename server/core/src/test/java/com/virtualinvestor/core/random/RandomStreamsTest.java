package com.virtualinvestor.core.random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RandomStreamsTest {

    @Test
    void sameSeedProducesIdenticalSequencesPerSubsystem() {
        long[] fromFirst = draws(new RandomStreams(42L), "fundamentals");
        long[] fromSecond = draws(new RandomStreams(42L), "fundamentals");
        assertArrayEquals(fromFirst, fromSecond);
    }

    @Test
    void subsystemsAreIndependent() {
        long[] fundamentals = draws(new RandomStreams(42L), "fundamentals");
        long[] bots = draws(new RandomStreams(42L), "bots");
        assertNotEquals(Arrays.toString(fundamentals), Arrays.toString(bots));
    }

    @Test
    void differentSeedsDiffer() {
        long[] first = draws(new RandomStreams(1L), "news");
        long[] second = draws(new RandomStreams(2L), "news");
        assertNotEquals(Arrays.toString(first), Arrays.toString(second));
    }

    @Test
    void subsystemDrawsDoNotShiftOtherStreams() {
        RandomStreams streams = new RandomStreams(7L);
        long firstFromAlphaBefore = streams.stream("alpha").nextLong();
        // Draw many values from another subsystem.
        RandomStreams same = new RandomStreams(7L);
        same.stream("beta").nextLong();
        same.stream("beta").nextLong();
        long firstFromAlphaAfter = same.stream("alpha").nextLong();
        assertArrayEquals(new long[] {firstFromAlphaBefore}, new long[] {firstFromAlphaAfter});
    }

    private static long[] draws(RandomStreams streams, String subsystem) {
        DeterministicRandom random = streams.stream(subsystem);
        long[] values = new long[16];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextLong();
        }
        return values;
    }
}
