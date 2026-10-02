package com.virtualinvestor.core.participant;

import java.util.Objects;

/** The human player. Identical to a bot to the engine; its orders can arrive over the wire. */
public final class HumanParticipant implements Participant {

    private final String id;
    private final String displayName;
    private final Account account;

    public HumanParticipant(String id, String displayName, double openingCash) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.account = new Account(id, openingCash);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ParticipantType type() {
        return ParticipantType.HUMAN;
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
