package com.virtualinvestor.core.participant;

/** One row of the leaderboard: current equity and return on the participant's starting capital. */
public record Standing(
        int rank,
        String participantId,
        String displayName,
        double equity,
        double startingCapital,
        double returnFraction) {
}
