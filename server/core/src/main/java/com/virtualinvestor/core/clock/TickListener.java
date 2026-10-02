package com.virtualinvestor.core.clock;

/**
 * Notified once per completed simulation step.
 */
@FunctionalInterface
public interface TickListener {

    /**
     * @param tick          the 1-based tick that just completed
     * @param logicalMillis logical market time at that tick (tick * stepMillis)
     */
    void onTick(long tick, long logicalMillis);
}
