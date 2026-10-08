package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

/** Player-accounts 5.1: every fill deducts each counterparty's bank schedule. */
class FillFeeTest {

    private static final long MINUTE_MILLIS = 60_000L;

    @Test
    void feeScheduleMath() {
        assertEquals(2.0, FeeSchedule.fixed(0.01, 2.0).feeFor(30L), 1e-9, "minimum binds");
        assertEquals(5.0, FeeSchedule.fixed(0.01, 2.0).feeFor(500L), 1e-9, "per-share above the minimum");
        assertEquals(0.0, FeeSchedule.none().feeFor(1_000_000L), 1e-9);
    }

    @Test
    void bothCounterpartiesPayTheirOwnSchedule() {
        RunningRound round = round(11L);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        round.participants().forEach(p -> round.setFeeSchedule(p.id(), FeeSchedule.fixed(0.01, 2.0)));

        round.enqueue(new Command.PlaceOrder("fee-1", "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 30L,
                0.0, 0.0, 0L));
        round.step();

        // 30 shares at 0.01 would be 0.30, so the 2.00 ticket minimum binds for the human,
        // who appears in exactly this one fill.
        assertEquals(2.0, round.participant("human").account().feesPaid(), 1e-9);
        double total = round.participants().stream()
                .mapToDouble(p -> p.account().feesPaid()).sum();
        assertTrue(total > 2.0, "bot counterparties pay too, got " + total);
    }

    @Test
    void fillsPublishPositionAndAccountUpdates() {
        RunningRound round = round(11L);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        round.enqueue(new Command.PlaceOrder("pos-1", "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 30L,
                0.0, 0.0, 0L));
        java.util.List<com.virtualinvestor.core.event.GameEvent> events = round.step();

        var positions = events.stream()
                .filter(com.virtualinvestor.core.event.GameEvent.PositionUpdated.class::isInstance)
                .map(com.virtualinvestor.core.event.GameEvent.PositionUpdated.class::cast)
                .filter(p -> p.participantId().equals("human"))
                .toList();
        var accounts = events.stream()
                .filter(com.virtualinvestor.core.event.GameEvent.AccountUpdated.class::isInstance)
                .map(com.virtualinvestor.core.event.GameEvent.AccountUpdated.class::cast)
                .filter(a -> a.participantId().equals("human"))
                .toList();
        assertTrue(positions.size() > 0, "a fill must publish the position");
        assertTrue(accounts.size() > 0, "a fill must publish the account");
        assertEquals(30L, positions.get(positions.size() - 1).netQty());
        assertEquals(round.participant("human").account().cash(), accounts.get(accounts.size() - 1).cash(), 1e-9);
    }

    @Test
    void feeTotalsAreDeterministic() {
        assertEquals(totalFees(12L), totalFees(12L), 1e-9);
    }

    private static double totalFees(long seed) {
        RunningRound round = round(seed);
        round.start();
        for (int i = 0; i < 10; i++) {
            round.step();
        }
        round.participants().forEach(p -> round.setFeeSchedule(p.id(), FeeSchedule.fixed(0.02, 5.0)));
        for (int i = 0; i < 10; i++) {
            round.step();
        }
        return round.participants().stream().mapToDouble(p -> p.account().feesPaid()).sum();
    }

    private static RunningRound round(long seed) {
        return new RunningRound("round-fees", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
    }
}
