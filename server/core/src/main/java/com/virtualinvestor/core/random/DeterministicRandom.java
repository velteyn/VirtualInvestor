package com.virtualinvestor.core.random;

/**
 * SplitMix64 pseudo-random generator. Implemented here, rather than using
 * {@link java.util.Random}, so the algorithm is fixed by us and cannot change between
 * JDK versions. Every simulation subsystem receives its own instance so one consumer's
 * draws never perturb another's.
 */
public final class DeterministicRandom {

    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public DeterministicRandom(long seed) {
        this.state = seed;
    }

    /** Next raw 64-bit value. */
    public long nextLong() {
        long z = (state += GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        // Rejection sampling for an unbiased result.
        long limit = Integer.toUnsignedLong(-bound) % bound;
        long candidate;
        do {
            candidate = nextLong() & 0xFFFFFFFFL;
        } while (candidate < limit);
        return (int) (candidate % bound);
    }

    /** Standard normal via the Marsaglia polar method. */
    public double nextGaussian() {
        double u;
        double v;
        double s;
        do {
            u = 2.0 * nextDouble() - 1.0;
            v = 2.0 * nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }
}
