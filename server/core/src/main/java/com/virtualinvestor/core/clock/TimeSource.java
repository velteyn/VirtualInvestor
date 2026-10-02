package com.virtualinvestor.core.clock;

/**
 * Supplies wall-clock milliseconds to the runtime driver. Injected so the real-time
 * loop can be driven by a fake source in tests; the simulation's own logical time is
 * derived from the tick counter and never read from here.
 */
@FunctionalInterface
public interface TimeSource {

    /** Current wall-clock time in milliseconds. */
    long nowMillis();

    /** The real system clock. */
    static TimeSource system() {
        return System::currentTimeMillis;
    }
}
