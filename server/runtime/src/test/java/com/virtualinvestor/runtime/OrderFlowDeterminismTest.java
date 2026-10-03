package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 3: flow impact stays deterministic and bounded. */
class OrderFlowDeterminismTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static List<GameEvent> runWithFlow(long seed, int ticks) {
        RunningRound round = new RunningRound("round-det", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        List<GameEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            if (i == 4) {
                double ask = round.engines().get("NVDO").book().bestAskPrice();
                round.enqueue(new Command.PlaceOrder(
                        "seed-buy", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 200L, ask, 0.0, 0L));
            }
            all.addAll(round.step());
        }
        return all;
    }

    @Test
    void sameSeedWithFlowProducesIdenticalEventStream() {
        assertEquals(runWithFlow(123L, 60), runWithFlow(123L, 60));
    }

    @Test
    void sustainedFlowStaysBoundedAroundTheReference() {
        RunningRound round = new RunningRound("round-bounded", 321L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();

        double worstGap = 0.0;
        for (int i = 0; i < 40; i++) {
            // Push a continuous one-sided flow into NVDO.
            round.enqueue(new Command.PlaceOrder(
                    "push-" + i, "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 50L, 0.0, 0.0, 0L));
            round.step();
            double reference = round.fundamentals().reference("NVDO");
            double gap = Math.abs(round.fundamentals().fundamental("NVDO") / reference - 1.0);
            worstGap = Math.max(worstGap, gap);
        }

        // Reversion keeps the flow-driven deviation within a sane band (well under 50%).
        double measuredGap = worstGap;
        assertTrue(measuredGap < 0.5, () -> "fundamental drifted too far from its reference: " + measuredGap);
    }
}
