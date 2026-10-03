package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

/** Group 2: the runtime accumulates flow and the market reacts to it. */
class OrderFlowRuntimeTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-flow", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        return round;
    }

    @Test
    void aLargeBuyMovesTheFundamentalAndTheQuote() {
        RunningRound round = continuousRound(91L);
        double fundamentalBefore = round.fundamentals().fundamental("NVDO");

        // A crossing buy that takes several hundred shares.
        double ask = round.engines().get("NVDO").book().bestAskPrice();
        round.enqueue(new Command.PlaceOrder(
                "big-buy", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 500L, ask, 0.0, 0L));
        round.step(); // applies the command and records the flow
        round.step(); // applies the flow to the fundamental, bots re-quote

        double fundamentalAfter = round.fundamentals().fundamental("NVDO");
        assertNotEquals(fundamentalBefore, fundamentalAfter, "flow should move the fundamental");
        assertTrue(round.player().account().position("NVDO").netQty() > 0L);
    }

    @Test
    void impactAndReversionAreObservable() {
        RunningRound round = continuousRound(93L);
        assertTrue(round.fundamentals().orderFlowImpact() != null, "impact is enabled");
        assertTrue(round.fundamentals().reversionStrength() > 0.0, "reversion is enabled");

        double gap = round.fundamentalGap("NVDO");
        assertEquals(round.fundamentals().fundamental("NVDO") - round.fundamentals().reference("NVDO"),
                gap, 1e-9);
    }

    @Test
    void cumulativeBuyFlowIsPositive() {
        RunningRound round = continuousRound(92L);
        double ask = round.engines().get("NVDO").book().bestAskPrice();
        round.enqueue(new Command.PlaceOrder(
                "buy-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 100L, ask, 0.0, 0L));
        round.step();

        assertTrue(round.cumulativeFlow().getOrDefault("NVDO", 0.0) > 0.0,
                "cumulative buy flow should be positive");
    }
}
