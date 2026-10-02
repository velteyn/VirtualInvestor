package com.virtualinvestor.core.game;

import com.virtualinvestor.core.domain.RoundState;
import java.util.Objects;

/**
 * The lifecycle of a round. A round is fully described by its id, seed and launch
 * configuration, so it can be reconstructed; this class guards the legal state transitions.
 */
public final class RoundLifecycle {

    private final String roundId;
    private final long seed;
    private final LaunchConfig config;

    private RoundState state = RoundState.CREATED;

    public RoundLifecycle(String roundId, long seed, LaunchConfig config) {
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.seed = seed;
        this.config = Objects.requireNonNull(config, "config");
    }

    public String roundId() {
        return roundId;
    }

    public long seed() {
        return seed;
    }

    public LaunchConfig config() {
        return config;
    }

    public RoundState state() {
        return state;
    }

    /** The immutable identity of the round, from which it can be rebuilt. */
    public String signature() {
        return "round=" + roundId + ";seed=" + seed
                + ";capital=" + config.startingCapital()
                + ";minutes=" + config.durationMinutes()
                + ";preset=" + config.preset().name();
    }

    public void transitionTo(RoundState next) {
        if (!allowed(state, next)) {
            throw new IllegalStateException("illegal round transition: " + state + " -> " + next);
        }
        state = next;
    }

    public static boolean allowed(RoundState current, RoundState next) {
        return switch (current) {
            case CREATED -> next == RoundState.WARMUP;
            case WARMUP -> next == RoundState.RUNNING;
            case RUNNING -> next == RoundState.CLOSING || next == RoundState.PAUSED;
            case PAUSED -> next == RoundState.RUNNING;
            case CLOSING -> next == RoundState.SETTLED;
            case SETTLED -> false;
        };
    }
}
