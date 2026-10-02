package com.virtualinvestor.core.clock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-step simulation clock. The world advances only through explicit {@link #step()}
 * calls, so a run is a pure function of the seed and the number of steps taken. A runtime
 * driver (see {@link RealTimeDriver}) invokes {@code step()} on wall-clock intervals, but
 * the clock's logical time is always {@code tick * stepMillis} and never the wall clock.
 */
public final class WorldClock {

    public static final long DEFAULT_STEP_MILLIS = 100L;

    public enum State {
        STOPPED,
        RUNNING,
        PAUSED
    }

    private final long stepMillis;
    private final TimeSource timeSource;
    private final List<TickListener> listeners = new ArrayList<>();

    private State state = State.STOPPED;
    private long tick = 0L;

    public WorldClock(long stepMillis, TimeSource timeSource) {
        if (stepMillis <= 0L) {
            throw new IllegalArgumentException("stepMillis must be positive, was " + stepMillis);
        }
        this.stepMillis = stepMillis;
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    public WorldClock() {
        this(DEFAULT_STEP_MILLIS, TimeSource.system());
    }

    public long stepMillis() {
        return stepMillis;
    }

    public TimeSource timeSource() {
        return timeSource;
    }

    public synchronized State state() {
        return state;
    }

    /** Current tick; 0 before the first step. */
    public synchronized long tick() {
        return tick;
    }

    /** Logical market time in milliseconds. */
    public synchronized long logicalMillis() {
        return tick * stepMillis;
    }

    public synchronized void addListener(TickListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public synchronized void removeListener(TickListener listener) {
        listeners.remove(listener);
    }

    /** Begin running. Does not advance time by itself; the driver or the caller steps. */
    public synchronized void start() {
        if (state == State.STOPPED) {
            state = State.RUNNING;
        }
    }

    /** Stop entirely, freezing the tick where it stands. */
    public synchronized void stop() {
        state = State.STOPPED;
    }

    /** Suspend advancement without ending the run. */
    public synchronized void pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
        }
    }

    public synchronized void resume() {
        if (state == State.PAUSED) {
            state = State.RUNNING;
        }
    }

    /**
     * Advance exactly one step when running, notifying listeners in registration order.
     *
     * @return true if the clock advanced, false when stopped or paused
     */
    public synchronized boolean step() {
        if (state != State.RUNNING) {
            return false;
        }
        tick++;
        long logical = tick * stepMillis;
        for (TickListener listener : listeners) {
            listener.onTick(tick, logical);
        }
        return true;
    }

    /**
     * Advance up to {@code steps} steps; stops early if the clock is no longer running.
     *
     * @return the number of steps actually taken
     */
    public synchronized int step(int steps) {
        if (steps < 0) {
            throw new IllegalArgumentException("steps must not be negative");
        }
        int taken = 0;
        while (taken < steps && step()) {
            taken++;
        }
        return taken;
    }
}
