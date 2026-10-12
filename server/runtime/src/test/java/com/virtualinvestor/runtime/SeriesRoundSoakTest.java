package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression cover for the frozen-round incident: a full session with live
 * series quoting and series order flow must advance every tick, settle, and
 * never throw out of step() — including through a degenerate underlying mark
 * that would previously have killed the world driver silently.
 */
class SeriesRoundSoakTest {

    private static final long MINUTE_MILLIS = 60_000L;

    @Test
    void fullSessionWithSeriesActivityAdvancesAndSettles() {
        RunningRound round = new RunningRound("round-soak", 99L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        List<Long> ticks = new ArrayList<>();
        int seriesOrders = 0;
        for (int i = 0; i < 200 && !round.settled(); i++) {
            if (i == 5) {
                seriesOrders += place(round, "soak-f1", "NVDO-F", OrderSide.BUY, 5L);
            }
            if (i == 8) {
                seriesOrders += place(round, "soak-c1", "NVDO-C640", OrderSide.BUY, 3L);
            }
            if (i == 12) {
                seriesOrders += place(round, "soak-p1", "NVDO-P640", OrderSide.BUY, 3L);
            }
            if (i == 16) {
                // Degenerate mark: quoting and marks must survive, not throw.
                round.engines().get("NVDO").setLastPrice(0.0);
            }
            if (i == 20) {
                seriesOrders += place(round, "soak-f2", "NVDO-F", OrderSide.SELL, 5L);
            }
            List<GameEvent> events = round.step();
            ticks.add(round.tick());
            assertTrue(events != null);
        }

        assertTrue(seriesOrders >= 3, "series orders were exercised");
        assertTrue(round.settled(), "the round must settle by the close");
        assertEquals(RoundState.SETTLED, round.state());
        for (int i = 1; i < ticks.size(); i++) {
            assertTrue(ticks.get(i) > ticks.get(i - 1), "ticks advance monotonically");
        }
        assertTrue(round.participant("human").account().positions().isEmpty(),
                "nothing survives the bell");
    }

    private static int place(RunningRound round, String id, String symbol, OrderSide side, long qty) {
        Double mark = round.marks().get(symbol);
        double price = mark == null || mark <= 0.0 ? 1.0 : mark;
        round.enqueue(new Command.PlaceOrder(id, "human", symbol, side, OrderType.LIMIT, qty, price, 0.0, 0L));
        return 1;
    }
}
