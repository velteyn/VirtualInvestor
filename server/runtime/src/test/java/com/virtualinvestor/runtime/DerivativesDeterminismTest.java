package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.core.state.WorldStateHasher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Group 8.1: seed+log replay reproduces every futures balance, premium, and fee
 * exactly. The hasher covers the new settlement events through the generic
 * record codec, so identical streams imply identical hashes.
 */
class DerivativesDeterminismTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private record Run(List<GameEvent> events, String hash, String account) {
    }

    private static Run runDerivatives(long seed) {
        RunningRound round = new RunningRound("round-det-deriv", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.setFeeSchedule("human", FeeSchedule.fixed(0.01, 2.0, 0.001, 1.0));
        round.start();
        List<GameEvent> all = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            if (i == 4) {
                double future = round.marks().get("NVDO-F");
                round.enqueue(new Command.PlaceOrder(
                        "det-f1", "human", "NVDO-F", OrderSide.BUY, OrderType.LIMIT, 5L, future, 0.0, 0L));
            }
            if (i == 6) {
                double future = round.marks().get("NVDO-F");
                round.enqueue(new Command.PlaceOrder(
                        "det-f2", "human", "NVDO-F", OrderSide.SELL, OrderType.LIMIT, 5L, future, 0.0, 0L));
            }
            if (i == 8) {
                double call = round.marks().get("NVDO-C640");
                if (call > 0.0) {
                    round.enqueue(new Command.PlaceOrder(
                            "det-c1", "human", "NVDO-C640", OrderSide.BUY, OrderType.LIMIT, 3L, call, 0.0,
                            0L));
                }
            }
            if (i == 10) {
                double call = round.marks().get("NVDO-C640");
                if (call > 0.0) {
                    round.enqueue(new Command.PlaceOrder(
                            "det-c2", "human", "NVDO-C640", OrderSide.SELL, OrderType.LIMIT, 3L, call, 0.0,
                            0L));
                }
            }
            all.addAll(round.step());
        }
        var account = round.participant("human").account();
        String snapshot = account.cash() + "|" + account.realizedPnl() + "|" + account.feesPaid() + "|"
                + account.lockedMargin() + "|" + account.positions();
        return new Run(List.copyOf(all), WorldStateHasher.hash(all), snapshot);
    }

    @Test
    void sameSeedWithDerivativesProducesIdenticalStreamsBalancesAndFees() {
        Run first = runDerivatives(77L);
        Run second = runDerivatives(77L);

        assertEquals(first.events(), second.events());
        assertEquals(first.hash(), second.hash());
        assertEquals(first.account(), second.account());
    }
}
