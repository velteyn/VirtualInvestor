package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.domain.SessionPhase;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 2: warm-up, phase gating and settlement. Uses a 1-minute step so a 60-tick session. */
class SessionLifecycleTest {

    private static final long MINUTE_MILLIS = 60_000L;
    private static final long DURATION_MINUTES = 60L;

    private static RunningRound round(long seed) {
        return new RunningRound("round-session", seed,
                new LaunchConfig(100_000.0, DURATION_MINUTES, RealismPreset.REALISTIC), MINUTE_MILLIS);
    }

    @Test
    void startGeneratesHistoryAndSeedsBooks() {
        RunningRound round = round(11L);
        assertEquals(0, round.history().size(), "no history before start");

        round.start();

        assertEquals(RoundState.RUNNING, round.state());
        assertEquals(21, round.history().size());
        assertEquals(60, round.history().get("NVDO").size());
        assertTrue(round.engines().get("NVDO").book().orderCount() > 0);
    }

    @Test
    void ordersOutsideContinuousTradingAreRefused() {
        RunningRound round = round(12L);
        round.start();
        assertEquals(SessionPhase.PRE_OPEN, round.session().phase());

        round.enqueue(new Command.PlaceOrder("cmd-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, 1.0, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.OrderRejected
                && ((GameEvent.OrderRejected) e).reason().contains("not in continuous")),
                "a pre-open order must be refused");
    }

    @Test
    void ordersAcceptedDuringContinuousTrading() {
        RunningRound round = round(13L);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        assertEquals(SessionPhase.CONTINUOUS, round.session().phase());

        double farBelow = round.fundamentals().fundamental("NVDO") * 0.5;
        round.enqueue(new Command.PlaceOrder("cmd-2", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, farBelow, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.OrderRested
                && ((GameEvent.OrderRested) e).orderId().equals("cmd-2")), "the order should rest");
        assertFalse(events.stream().anyMatch(e -> e instanceof GameEvent.OrderRejected
                && ((GameEvent.OrderRejected) e).orderId().equals("cmd-2")));
    }

    @Test
    void roundSettlesAtTheCloseAndRefusesFurtherOrders() {
        RunningRound round = round(14L);
        round.start();

        for (int i = 0; i < 120 && !round.settled(); i++) {
            round.step();
        }

        assertTrue(round.settled(), "the round should settle by the close");
        assertEquals(RoundState.SETTLED, round.state());
        assertThrows(IllegalStateException.class, round::step, "a settled round refuses to advance");
    }
}
