package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Standing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarginAndSettlementTest {

    private static final MarginManager MARGIN = new MarginManager(0.25);

    @Test
    void leveragedPositionIsLiquidatedOnMarginCall() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        // 2x leverage: 2000 shares at 100 uses 200k of buying power.
        trader.account().applyFill("ACME", OrderSide.BUY, 2000L, 100.0);

        Map<String, Double> marks = Map.of("ACME", 90.0);
        assertNull(MARGIN.check(trader, marks), "80k equity vs 45k requirement is fine");

        marks = Map.of("ACME", 60.0);
        MarginCall call = MARGIN.check(trader, marks);
        assertNotNull(call);
        assertEquals(20_000.0, call.equity(), 1e-9);
        assertEquals(30_000.0, call.requirement(), 1e-9);

        List<GameEvent> events = MARGIN.enforce(trader, marks, 500L);
        assertTrue(events.stream().anyMatch(GameEvent.MarginCallIssued.class::isInstance));
        assertTrue(events.stream().anyMatch(GameEvent.LiquidationExecuted.class::isInstance));
        assertTrue(trader.account().position("ACME").isFlat(), "position should be closed");
        assertEquals(20_000.0, trader.account().cash(), 1e-9);
    }

    @Test
    void settlementClosesPositionsAndFinalizesRanking() {
        HumanParticipant player = new HumanParticipant("h1", "You", 100_000.0);
        HumanParticipant rival = new HumanParticipant("r1", "MARK-like", 100_000.0);
        player.account().applyFill("ACME", OrderSide.BUY, 100L, 100.0);
        rival.account().applyFill("ACME", OrderSide.BUY, 100L, 100.0);

        Map<String, Double> closing = Map.of("ACME", 120.0);
        Map<String, Double> capitals = new LinkedHashMap<>();
        capitals.put("h1", 100_000.0);
        capitals.put("r1", 100_000.0);

        Settlement.Result result = Settlement.settle(List.of(player, rival), closing, capitals, 18_000L, "round-1");

        assertTrue(player.account().position("ACME").isFlat());
        assertTrue(rival.account().position("ACME").isFlat());
        assertEquals(102_000.0, player.account().equity(closing), 1e-9);

        List<Standing> standings = result.standings();
        assertEquals(2, standings.size());
        assertEquals(1, standings.get(0).rank());
        assertEquals(0.02, standings.get(0).returnFraction(), 1e-9);
        assertTrue(result.events().stream().anyMatch(GameEvent.LiquidationExecuted.class::isInstance));
        assertTrue(result.events().stream().anyMatch(GameEvent.RoundSettled.class::isInstance));
        assertFalse(result.events().isEmpty());
    }
}
