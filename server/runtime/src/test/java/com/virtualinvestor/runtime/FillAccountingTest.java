package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

/** Group 3.4: every fill is applied to both counterparties' accounts. */
class FillAccountingTest {

    private static final long MINUTE_MILLIS = 60_000L;

    @Test
    void aMatchedOrderCreditsBothSides() {
        RunningRound round = new RunningRound("round-fills", 41L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        // Step into continuous trading (tick 4), where MARK has posted its first quote.
        for (int i = 0; i < 4; i++) {
            round.step();
        }

        // A crossing limit buy takes MARK's ask.
        double ask = round.engines().get("NVDO").book().bestAskPrice();
        round.enqueue(new Command.PlaceOrder(
                "buy-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, ask, 0.0, 0L));
        round.step();

        long humanPosition = round.player().account().position("NVDO").netQty();
        assertTrue(humanPosition > 0L, "the buyer should hold a position, was " + humanPosition);

        // Every share bought came from someone: aggregate net position is zero.
        long aggregate = 0L;
        for (Participant participant : round.participants()) {
            aggregate += participant.account().position("NVDO").netQty();
        }
        assertEquals(0L, aggregate, "both counterparties must be credited");
    }
}
