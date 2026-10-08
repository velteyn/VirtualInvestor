package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 3.2: pre-trade validation with explicit rejection reasons. */
class OrderIntakeValidationTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-intake", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        return round;
    }

    private static String rejectionReason(List<GameEvent> events, String orderId) {
        for (GameEvent event : events) {
            if (event instanceof GameEvent.OrderReport rejected
                    && rejected.event() == com.virtualinvestor.core.domain.OrderEventKind.REJECTED
                    && rejected.orderId().equals(orderId)) {
                return rejected.reason();
            }
        }
        return null;
    }

    @Test
    void offTickPriceRejected() {
        RunningRound round = continuousRound(21L);
        round.enqueue(new Command.PlaceOrder("t1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, 10.005, 0.0, 0L));
        String reason = rejectionReason(round.step(), "t1");
        assertTrue(reason != null && reason.contains("tick"), "expected a tick rejection, got: " + reason);
    }

    @Test
    void nonPositiveQuantityRejected() {
        RunningRound round = continuousRound(22L);
        round.enqueue(new Command.PlaceOrder("t2", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 0L, 10.0, 0.0, 0L));
        String reason = rejectionReason(round.step(), "t2");
        assertTrue(reason != null && reason.contains("positive"), "expected a quantity rejection, got: " + reason);
    }

    @Test
    void shortingTheIndexRejected() {
        RunningRound round = continuousRound(23L);
        double high = Math.round(round.fundamentals().fundamental("VNDX") * 2.0 * 100.0) / 100.0;
        round.enqueue(new Command.PlaceOrder("t3", "human", "VNDX", OrderSide.SELL, OrderType.LIMIT, 10L, high, 0.0, 0L));
        String reason = rejectionReason(round.step(), "t3");
        assertTrue(reason != null && reason.contains("short"), "expected a short-sale rejection, got: " + reason);
    }

    @Test
    void haltedInstrumentRejected() {
        RunningRound round = continuousRound(24L);
        long tick = round.tick();
        // Force a halt by pushing the price far beyond the band.
        round.breakers().get("NVDO").onPrice(round.fundamentals().fundamental("NVDO") * 3.0, tick);

        round.enqueue(new Command.PlaceOrder("t4", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, 1.0, 0.0, 0L));
        String reason = rejectionReason(round.step(), "t4");
        assertTrue(reason != null && reason.contains("halted"), "expected a halt rejection, got: " + reason);
    }
}
