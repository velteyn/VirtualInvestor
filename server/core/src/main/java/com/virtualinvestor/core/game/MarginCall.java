package com.virtualinvestor.core.game;

/** A participant's equity has fallen below the maintenance requirement. */
public record MarginCall(String participantId, double equity, double requirement) {
}
