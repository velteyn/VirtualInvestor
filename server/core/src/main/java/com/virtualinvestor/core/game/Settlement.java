package com.virtualinvestor.core.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Ranking;
import com.virtualinvestor.core.participant.Standing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ends a round: marks all positions to the closing price, force-closes every position (pure
 * intraday, so nobody carries a position past the close), computes final equity, and produces
 * the final ranking.
 */
public final class Settlement {

    /** The final standings and the events produced by closing every position. */
    public record Result(List<Standing> standings, List<GameEvent> events) {
        public Result {
            standings = List.copyOf(standings);
            events = List.copyOf(events);
        }
    }

    private Settlement() {
    }

    public static Result settle(
            Collection<Participant> participants,
            Map<String, Double> closingMarks,
            Map<String, Double> startingCapitals,
            long tick,
            String roundId) {
        List<GameEvent> events = new ArrayList<>();
        for (Participant participant : participants) {
            events.addAll(MarginManager.forceClose(participant, closingMarks, tick));
        }
        List<Standing> standings = Ranking.standings(participants, closingMarks, startingCapitals);
        events.add(new GameEvent.RoundSettled(roundId, tick));
        return new Result(standings, events);
    }
}
