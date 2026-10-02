package com.virtualinvestor.core.participant;

import java.util.Objects;

/**
 * Cash, positions and buying power for one participant. Position accounting, mark-to-market
 * equity and margin arrive in task 6.2; this task establishes the account as the shared
 * reference every participant type holds.
 */
public final class Account {

    private final String participantId;
    private long cash;

    public Account(String participantId, long openingCash) {
        this.participantId = Objects.requireNonNull(participantId, "participantId");
        if (openingCash < 0L) {
            throw new IllegalArgumentException("openingCash must not be negative");
        }
        this.cash = openingCash;
    }

    public String participantId() {
        return participantId;
    }

    public long cash() {
        return cash;
    }

    public void adjustCash(long delta) {
        cash += delta;
    }

    /** Cash available to open new positions. Margin-aware buying power lands in task 6.2. */
    public long buyingPower() {
        return cash;
    }
}
