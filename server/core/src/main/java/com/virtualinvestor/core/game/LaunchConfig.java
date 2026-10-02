package com.virtualinvestor.core.game;

import com.virtualinvestor.core.rules.RealismPreset;

/**
 * What the player chooses on the launch pad: how much money to start with, how long the round
 * lasts, and the realism preset. Values are validated against the allowed ranges so an invalid
 * configuration can never create a round.
 */
public record LaunchConfig(double startingCapital, long durationMinutes, RealismPreset preset) {

    public static final double MIN_CAPITAL = 10_000.0;
    public static final double MAX_CAPITAL = 5_000_000.0;
    public static final long MIN_MINUTES = 15L;
    public static final long MAX_MINUTES = 240L;

    public LaunchConfig {
        if (startingCapital < MIN_CAPITAL || startingCapital > MAX_CAPITAL) {
            throw new IllegalArgumentException(
                    "startingCapital must be within [" + (long) MIN_CAPITAL + ", " + (long) MAX_CAPITAL + "]");
        }
        if (durationMinutes < MIN_MINUTES || durationMinutes > MAX_MINUTES) {
            throw new IllegalArgumentException(
                    "durationMinutes must be within [" + MIN_MINUTES + ", " + MAX_MINUTES + "]");
        }
        if (preset == null) {
            throw new IllegalArgumentException("preset is required");
        }
    }

    public long durationTicks(long stepMillis) {
        if (stepMillis <= 0L) {
            throw new IllegalArgumentException("stepMillis must be positive");
        }
        return durationMinutes * 60_000L / stepMillis;
    }
}
