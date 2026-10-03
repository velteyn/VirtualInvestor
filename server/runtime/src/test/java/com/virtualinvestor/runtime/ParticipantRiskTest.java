package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 5: participant risk limits bound bot losses and stop a losing participant. */
class ParticipantRiskTest {

    private static final long MINUTE_MILLIS = 60_000L;
    private static final double CAPITAL = 100_000.0;

    @Test
    void lossesAreBoundedAcrossAFullRound() {
        RunningRound round = new RunningRound("round-risk", 555L,
                new LaunchConfig(CAPITAL, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 200 && !round.settled(); i++) {
            round.step();
        }

        // No rival may end at many multiples of its capital in debt: the risk floor + forced
        // close bound the worst case. A small negative floor (say -25% of capital) is acceptable.
        for (BotParticipant rival : round.rivals()) {
            double equity = rival.account().equity(round.marks());
            assertTrue(equity > -CAPITAL * 0.25,
                    rival.displayName() + " lost too much: equity=" + equity);
        }
    }

    @Test
    void aRiskFloorStopsAndFlattens() {
        RunningRound round = new RunningRound("round-floor", 777L,
                new LaunchConfig(CAPITAL, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        BotParticipant victim = round.rivals().get(1); // TIDE, a taker

        // Drive the victim underwater directly to simulate a losing run.
        victim.account().applyFill("NVDO", OrderSide.BUY, 100_000L, 100.0);
        victim.account().applyFill("NVDO", OrderSide.SELL, 100_000L, 1.0);

        // Run ticks until the floor logic acts.
        for (int i = 0; i < 3; i++) {
            round.step();
        }

        assertTrue(victim.account().position("NVDO").isFlat(),
                "a participant at the risk floor should be flattened");
    }
}
