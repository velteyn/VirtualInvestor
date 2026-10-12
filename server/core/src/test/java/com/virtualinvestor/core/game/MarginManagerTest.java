package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.HumanParticipant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarginManagerTest {

    @Test
    void noCallWhenCovered() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        trader.account().applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);

        assertNull(new MarginManager(0.25).check(trader, Map.of("NVDO-F", 650.0)));
    }

    @Test
    void futuresExposureTriggersCall() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        trader.account().applyFuturesFill("NVDO-F", OrderSide.SELL, 150L, 640.0, 128.0);

        Map<String, Double> marks = Map.of("NVDO-F", 2000.0);
        MarginCall call = new MarginManager(0.50).check(trader, marks);
        assertNotNull(call);
        assertEquals(-104_000.0, call.equity(), 1e-6);
        assertEquals(150_000.0, call.requirement(), 1e-6);
    }

    @Test
    void enforceClosesMostLeveredFirst() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        trader.account().applyFill("ACME", OrderSide.BUY, 100L, 100.0);
        trader.account().applyFuturesFill("NVDO-F", OrderSide.SELL, 100L, 640.0, 128.0);

        // Short variation: (640 - 1280) * 100 = -64k; equity 36k vs 69k requirement.
        Map<String, Double> marks = Map.of("ACME", 100.0, "NVDO-F", 1280.0);
        List<GameEvent> events = new MarginManager(0.50).enforce(trader, marks, 7L);

        assertTrue(events.stream().anyMatch(GameEvent.MarginCallIssued.class::isInstance));
        assertTrue(trader.account().position("NVDO-F").isFlat());
        assertTrue(trader.account().position("ACME").isFlat(), "a call still flattens everything");
        List<String> liquidated = events.stream()
                .filter(GameEvent.LiquidationExecuted.class::isInstance)
                .map(e -> ((GameEvent.LiquidationExecuted) e).symbol())
                .toList();
        assertEquals(List.of("NVDO-F", "ACME"), liquidated, "most levered closes first");
        assertEquals(-64_000.0 + 0.0, trader.account().realizedFor("NVDO-F"), 1e-6);
    }

    @Test
    void forceCloseSettlesFuturesInCash() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        trader.account().applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);

        List<GameEvent> events =
                MarginManager.forceClose(trader, Map.of("NVDO-F", 650.0), 9L);

        assertTrue(trader.account().position("NVDO-F").isFlat());
        assertEquals(100.0, trader.account().realizedPnl(), 1e-6);
        assertEquals(100_100.0, trader.account().cash(), 1e-6);
        assertEquals(0.0, trader.account().lockedMargin(), 1e-9);
        assertTrue(events.stream().anyMatch(GameEvent.LiquidationExecuted.class::isInstance));
    }

    @Test
    void forceCloseSettlesOptionsAtPremium() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        trader.account().applyOptionFill("NVDO-C640", OrderSide.BUY, 5L, 12.0);

        MarginManager.forceClose(trader, Map.of("NVDO-C640", 20.0), 9L);

        assertTrue(trader.account().position("NVDO-C640").isFlat());
        assertEquals(40.0, trader.account().realizedPnl(), 1e-6);
        assertFalse(trader.account().positions().containsKey("NVDO-C640"));
    }
}
