package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.ParticipantType;
import com.virtualinvestor.core.participant.Ranking;
import com.virtualinvestor.core.participant.Standing;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RankingTest {

    @Test
    void rivalsAreSeededWithThePlayersCapital() {
        List<BotParticipant> rivals = RivalRoster.rivals(100_000.0);
        assertEquals(5, rivals.size());
        for (BotParticipant rival : rivals) {
            assertEquals(100_000.0, rival.account().cash(), 0.0);
        }
    }

    @Test
    void standingsRankByEquityWithReturnOnCapital() {
        HumanParticipant player = new HumanParticipant("h1", "You", 100_000.0);
        List<BotParticipant> rivals = new ArrayList<>(RivalRoster.rivals(100_000.0));
        List<BotParticipant> institutions = new ArrayList<>(RivalRoster.institutions(100_000.0));

        // Player loses 10% on a long; MARK gains 20%.
        player.account().applyFill("ACME", OrderSide.BUY, 1000L, 100.0);
        player.account().applyFill("ACME", OrderSide.SELL, 1000L, 90.0);
        BotParticipant mark = rivals.get(0);
        mark.account().applyFill("ACME", OrderSide.BUY, 1000L, 100.0);
        mark.account().applyFill("ACME", OrderSide.SELL, 1000L, 120.0);

        List<Participant> everyone = new ArrayList<>();
        everyone.add(player);
        everyone.addAll(rivals);
        everyone.addAll(institutions);

        Map<String, Double> startingCapitals = new LinkedHashMap<>();
        for (Participant participant : everyone) {
            startingCapitals.put(participant.id(), 100_000.0);
        }

        List<Standing> standings = Ranking.standings(everyone, Map.of(), startingCapitals);

        assertEquals(6, standings.size(), "five rivals plus the player, institutions excluded");
        assertFalse(standings.stream().anyMatch(s -> s.participantId().startsWith("inst-")));
        assertEquals("rival-1", standings.get(0).participantId(), "MARK leads");
        assertEquals(120_000.0, standings.get(0).equity(), 1e-9);
        assertEquals(0.20, standings.get(0).returnFraction(), 1e-9);
        Standing last = standings.get(standings.size() - 1);
        assertEquals("h1", last.participantId());
        assertEquals(-0.10, last.returnFraction(), 1e-9);
        for (int i = 1; i < standings.size(); i++) {
            assertTrue(standings.get(i - 1).equity() >= standings.get(i).equity());
            assertEquals(i + 1, standings.get(i).rank());
        }
    }
}
