package com.virtualinvestor.core.participant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Produces the round leaderboard. It ranks by mark-to-market equity (fair because rivals are
 * seeded with the player's capital) and reports each participant's return on starting capital
 * as a secondary figure. Institutions are excluded by {@link Leaderboard}.
 */
public final class Ranking {

    private Ranking() {
    }

    public static List<Standing> standings(
            Collection<Participant> participants, Map<String, Double> marks, Map<String, Double> startingCapitals) {
        List<Participant> ranked = Leaderboard.ranked(participants, marks);
        List<Standing> standings = new ArrayList<>(ranked.size());
        int rank = 1;
        for (Participant participant : ranked) {
            double equity = participant.account().equity(marks);
            double capital = startingCapitals.getOrDefault(participant.id(), 0.0);
            double returnFraction = capital > 0.0 ? equity / capital - 1.0 : 0.0;
            standings.add(new Standing(rank++, participant.id(), participant.displayName(), equity, capital, returnFraction));
        }
        return List.copyOf(standings);
    }
}
