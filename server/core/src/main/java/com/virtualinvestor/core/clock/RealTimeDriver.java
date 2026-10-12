package com.virtualinvestor.core.clock;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives a {@link WorldClock} at its step interval on a background thread. Kept out of the
 * simulation path: the clock still advances only through {@code step()}, so the same run can
 * be replayed headlessly without this driver.
 */
public final class RealTimeDriver implements AutoCloseable {

    private final WorldClock clock;
    private final Runnable tick;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public RealTimeDriver(WorldClock clock) {
        this(clock, clock::step);
    }

    /**
     * Drive {@code tick} every {@code clock.stepMillis()}. Use this when the world's own step
     * (not the bare clock) must run each interval; the clock still gates state and timing.
     */
    public RealTimeDriver(WorldClock clock, Runnable tick) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tick = Objects.requireNonNull(tick, "tick");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vi-world-clock");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Start the clock and run the tick every {@code stepMillis}. */
    public synchronized void start() {
        if (task != null) {
            return;
        }
        clock.start();
        // A throwing tick would silently cancel all future executions
        // (ScheduledExecutorService semantics), freezing the round with no log.
        // Catch, log, and continue so one bad tick can never kill the world.
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                tick.run();
            } catch (Throwable failure) {
                java.util.logging.Logger.getLogger(RealTimeDriver.class.getName())
                        .log(java.util.logging.Level.SEVERE, "world tick failed, continuing", failure);
            }
        }, 0L, clock.stepMillis(), TimeUnit.MILLISECONDS);
    }

    /** Pause the clock while keeping the driver alive. */
    public synchronized void pause() {
        clock.pause();
    }

    public synchronized void resume() {
        clock.resume();
    }

    /** Cancel the schedule and stop the clock. */
    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        clock.stop();
    }

    @Override
    public synchronized void close() {
        stop();
        scheduler.shutdownNow();
    }
}
