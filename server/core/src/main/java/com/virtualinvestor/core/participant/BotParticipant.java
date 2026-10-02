package com.virtualinvestor.core.participant;

import java.util.Objects;

/**
 * A virtual trader — a named rival or an institution. It is deliberately the same shape as a
 * human participant; the only difference to the engine is its type and, later, the strategy
 * that decides its orders.
 */
public final class BotParticipant implements Participant {

    private final String id;
    private final String displayName;
    private final ParticipantType type;
    private final Account account;

    public BotParticipant(String id, String displayName, ParticipantType type, double openingCash) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        if (type == ParticipantType.HUMAN) {
            throw new IllegalArgumentException("BotParticipant must be a rival bot or institution");
        }
        this.type = type;
        this.account = new Account(id, openingCash);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ParticipantType type() {
        return type;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Account account() {
        return account;
    }
}
