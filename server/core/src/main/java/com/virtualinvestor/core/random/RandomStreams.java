package com.virtualinvestor.core.random;

import java.nio.charset.StandardCharsets;

/**
 * Derives independent, reproducible random streams from a round seed. A subsystem asks for
 * its named stream and always receives the same sequence for the same round seed, so a run
 * is replayable and one subsystem's number of draws cannot shift another's.
 */
public final class RandomStreams {

    private final long roundSeed;

    public RandomStreams(long roundSeed) {
        this.roundSeed = roundSeed;
    }

    public long roundSeed() {
        return roundSeed;
    }

    /** A reproducible stream for the named subsystem. */
    public DeterministicRandom stream(String subsystem) {
        return new DeterministicRandom(mix(roundSeed, fnv1a(subsystem)));
    }

    private static long mix(long roundSeed, long nameHash) {
        long z = roundSeed ^ (nameHash + 0x9E3779B97F4A7C15L + (roundSeed << 6) + (roundSeed >>> 2));
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Stable FNV-1a hash; {@link String#hashCode} is avoided to keep naming explicit. */
    static long fnv1a(String text) {
        long hash = 0xCBF29CE484222325L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFFL);
            hash *= 0x100000001B3L;
        }
        return hash;
    }
}
