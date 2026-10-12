package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

/** Group 4.2: percentage-of-notional derivatives commissions with replay identity. */
class BrokerageDerivativesTest {

    private static final long MINUTE_MILLIS = 60_000L;
    private static final double EPS = 1e-9;

    @Test
    void seriesFeeIsMaxOfMinimumAndRateTimesNotional() {
        FeeSchedule schedule = FeeSchedule.fixed(0.01, 2.0, 0.001, 1.0);

        assertEquals(3.2, schedule.feeForSeries(5L, 3200.0), EPS);
        assertEquals(1.0, schedule.feeForSeries(1L, 640.0), EPS);
        assertEquals(2.0, schedule.feeFor(30L), EPS);
    }

    @Test
    void futuresFillPaysSeriesFeeOnBothSides() {
        RunningRound round = continuousRound(45L);
        FeeSchedule schedule = FeeSchedule.fixed(0.01, 2.0, 0.001, 1.0);
        round.setFeeSchedule("human", schedule);
        double last = round.marks().get("NVDO-F");

        round.enqueue(new Command.PlaceOrder("f1", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, last, 0.0, 0L));
        round.step();
        round.enqueue(new Command.PlaceOrder("f2", "human", "NVDO-F",
                OrderSide.SELL, OrderType.LIMIT, 5L, last, 0.0, 0L));
        round.step();

        // Self-match: the human is both taker and maker, so both sides pay.
        double expected = 2.0 * schedule.feeForSeries(5L, 5L * last);
        assertEquals(expected, round.participant("human").account().feesPaid(), 1e-6);
        assertTrue(expected > 0.0);
    }

    @Test
    void seriesFeeIsReplayIdentical() {
        FeeSchedule schedule = FeeSchedule.fixed(0.01, 2.0, 0.001, 1.0);
        assertEquals(schedule.feeForSeries(5L, 3200.0), schedule.feeForSeries(5L, 3200.0), EPS);
    }

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-fees", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        return round;
    }
}
