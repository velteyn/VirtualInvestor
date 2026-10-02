package com.virtualinvestor.core.game;

import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.ParticipantType;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the fixed cast of a round: the five named rivals the player competes with and the
 * institutions that act as the market. Everyone is seeded with the player's starting capital
 * so absolute-equity ranking is fair (spec: fair ranking).
 */
public final class RivalRoster {

    public static final List<String> RIVAL_NAMES = List.of("MARK", "TIDE", "LEDGER", "BLITZ", "VULTURE");
    public static final List<String> INSTITUTION_NAMES = List.of("ATLAS BANK", "MERIDIAN CAPITAL", "CROWN FUND");

    private RivalRoster() {
    }

    public static List<BotParticipant> rivals(double startingCapital) {
        List<BotParticipant> rivals = new ArrayList<>(RIVAL_NAMES.size());
        for (int i = 0; i < RIVAL_NAMES.size(); i++) {
            String name = RIVAL_NAMES.get(i);
            rivals.add(new BotParticipant("rival-" + (i + 1), name, ParticipantType.RIVAL_BOT, startingCapital));
        }
        return List.copyOf(rivals);
    }

    public static List<BotParticipant> institutions(double startingCapital) {
        List<BotParticipant> institutions = new ArrayList<>(INSTITUTION_NAMES.size());
        for (int i = 0; i < INSTITUTION_NAMES.size(); i++) {
            String name = INSTITUTION_NAMES.get(i);
            institutions.add(new BotParticipant("inst-" + (i + 1), name, ParticipantType.INSTITUTION, startingCapital));
        }
        return List.copyOf(institutions);
    }
}
