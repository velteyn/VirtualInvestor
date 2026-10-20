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
        assertEquals(21 + com.virtualinvestor.core.instrument.SeriesLister
                .list(com.virtualinvestor.core.instrument.InstrumentUniverse.defaultUniverse()).size(),
                round.history().size(), "spots plus every listed series");
        assertEquals(60, round.history().get("NVDO").size());
        assertEquals(60, round.history().get("NVDO-F").size());
        assertTrue(round.engines().get("NVDO").book().orderCount() > 0);
    }

    @Test
    void ordersOutsideContinuousTradingAreRefused() {
        RunningRound round = round(12L);
        round.start();
        assertEquals(SessionPhase.PRE_OPEN, round.session().phase());

        round.enqueue(new Command.PlaceOrder("cmd-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, 1.0, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.OrderReport r
                && r.event() == com.virtualinvestor.core.domain.OrderEventKind.REJECTED
                && r.reason().contains("not in continuous")),
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

        double farBelow = Math.round(round.fundamentals().fundamental("NVDO") * 0.5 * 100.0) / 100.0;
        round.enqueue(new Command.PlaceOrder("cmd-2", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, farBelow, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.OrderReport r
                && r.event() == com.virtualinvestor.core.domain.OrderEventKind.RESTED
                && r.orderId().equals("cmd-2")), "the order should rest");
        assertFalse(events.stream().anyMatch(e -> e instanceof GameEvent.OrderReport r
                && r.event() == com.virtualinvestor.core.domain.OrderEventKind.REJECTED
                && r.orderId().equals("cmd-2")));
    }

    @Test
    void uncrossedAuctionOrderIsCancelledWithReason() {
        RunningRound round = round(15L);
        round.start();
        for (int i = 0; i < 3; i++) {
            round.step();
        }
        assertEquals(SessionPhase.OPENING_AUCTION, round.session().phase());

        // A lone-sided auction order can never cross: the clear must report
        // it cancelled instead of stranding it as working on the client.
        round.enqueue(new Command.PlaceOrder("cmd-auc-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, 900.0, 0.0, 0L));
        List<GameEvent> across = round.step();
        assertEquals(SessionPhase.CONTINUOUS, round.session().phase());

        assertTrue(across.stream().anyMatch(e -> e instanceof GameEvent.OrderReport r
                && r.orderId().equals("cmd-auc-1")
                && r.event() == com.virtualinvestor.core.domain.OrderEventKind.CANCELLED
                && r.reason() != null && r.reason().contains("no match")),
                "an uncrossed auction order must be cancelled with a reason");
    }

    @Test
    void crossedAuctionOrdersReportFills() {
        RunningRound round = round(16L);
        round.start();
        for (int i = 0; i < 3; i++) {
            round.step();
        }
        assertEquals(SessionPhase.OPENING_AUCTION, round.session().phase());

        round.enqueue(new Command.PlaceOrder("cmd-auc-2", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, 900.0, 0.0, 0L));
        round.enqueue(new Command.PlaceOrder("cmd-auc-3", "rival-1", "NVDO", OrderSide.SELL, OrderType.LIMIT, 10L, 100.0, 0.0, 0L));
        List<GameEvent> across = round.step();

        assertTrue(across.stream().anyMatch(e -> e instanceof GameEvent.OrderReport r
                && r.orderId().equals("cmd-auc-2")
                && r.event() == com.virtualinvestor.core.domain.OrderEventKind.FILL
                && r.status() == com.virtualinvestor.core.domain.OrderStatus.FILLED),
                "a crossed auction buy must report its fill");
        assertTrue(across.stream().anyMatch(e -> e instanceof GameEvent.OrderReport r
                && r.orderId().equals("cmd-auc-3")
                && r.event() == com.virtualinvestor.core.domain.OrderEventKind.FILL),
                "a crossed auction sell must report its fill");
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
