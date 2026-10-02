package com.virtualinvestor.core.participant;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks participants by mark-to-market equity. Institutions are the market and never appear
 * on the player's leaderboard, so only humans and rival bots are ranked.
 */
public final class Leaderboard {

    private Leaderboard() {
    }

    public static List<Participant> ranked(Collection<Participant> participants, Map<String, Double> marks) {
        return participants.stream()
                .filter(participant -> participant.type() != ParticipantType.INSTITUTION)
                .sorted(Comparator
                        .comparingDouble((Participant participant) -> participant.account().equity(marks))
                        .reversed()
                        .thenComparing(Participant::id))
                .toList();
    }
}
