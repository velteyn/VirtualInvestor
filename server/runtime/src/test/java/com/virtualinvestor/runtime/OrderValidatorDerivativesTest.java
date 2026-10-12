package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.rules.MarketRules;
import com.virtualinvestor.core.rules.OrderValidator;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.core.rules.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 4.1: series-aware pre-trade validation with explicit rejection reasons. */
class OrderValidatorDerivativesTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-deriv", seed,
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
                    && rejected.event() == OrderEventKind.REJECTED
                    && rejected.orderId().equals(orderId)) {
                return rejected.reason();
            }
        }
        return null;
    }

    @Test
    void unknownSeriesRejected() {
        RunningRound round = continuousRound(41L);
        round.enqueue(new Command.PlaceOrder("d1", "human", "NOPE-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, 10.0, 0.0, 0L));
        String reason = rejectionReason(round.step(), "d1");
        assertTrue(reason != null && reason.contains("unknown instrument"),
                "expected an unknown-instrument rejection, got: " + reason);
    }

    @Test
    void offTickSeriesPriceRejected() {
        RunningRound round = continuousRound(42L);
        round.enqueue(new Command.PlaceOrder("d2", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, 640.005, 0.0, 0L));
        String reason = rejectionReason(round.step(), "d2");
        assertTrue(reason != null && reason.contains("tick"),
                "expected a tick rejection, got: " + reason);
    }

    @Test
    void subGridSeriesQuantityRejected() {
        Instrument series = new Instrument("NVDO-F", "Nvido Future", "Semis", InstrumentType.FUTURE,
                0L, 1.0, 0.01, 10L, 640.0, 0.4, 1.0, 1.0);
        ValidationResult result = OrderValidator.validate(
                MarketRules.forSeries(series, RealismPreset.REALISTIC),
                OrderSide.BUY, OrderType.LIMIT, 640.0, 15L);
        assertTrue(!result.valid() && result.reason().contains("multiple"),
                "expected a grid rejection, got: " + result.reason());
    }

    @Test
    void haltedUnderlyingHaltsSeries() {
        RunningRound round = continuousRound(43L);
        long tick = round.tick();
        round.breakers().get("NVDO").onPrice(round.fundamentals().fundamental("NVDO") * 3.0, tick);

        round.enqueue(new Command.PlaceOrder("d3", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, 640.0, 0.0, 0L));
        String reason = rejectionReason(round.step(), "d3");
        assertTrue(reason != null && reason.contains("NVDO") && reason.contains("halted"),
                "expected an underlying-halted rejection, got: " + reason);
    }

    @Test
    void seriesOrderAfterCloseRefused() {
        RunningRound round = continuousRound(44L);
        boolean reachedClosing = false;
        for (int i = 0; i < 200 && !reachedClosing; i++) {
            round.step();
            reachedClosing = round.session().phase()
                    == com.virtualinvestor.core.domain.SessionPhase.CLOSING_AUCTION;
        }
        assertTrue(reachedClosing, "expected to reach the closing auction");
        round.enqueue(new Command.PlaceOrder("d4", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, 640.0, 0.0, 0L));
        String reason = rejectionReason(round.step(), "d4");
        assertTrue(reason != null && reason.contains("continuous"),
                "expected an expiry rejection, got: " + reason);
    }

    @Test
    void seriesRulesInheritGrids() {
        Instrument series = new Instrument("NVDO-F", "Nvido Future", "Semis", InstrumentType.FUTURE,
                0L, 1.0, 0.01, 1L, 640.0, 0.4, 1.0, 1.0);
        MarketRules rules = MarketRules.forSeries(series, RealismPreset.REALISTIC);
        assertEquals(0.01, rules.tickSize());
        assertEquals(1L, rules.quantityGrid());
        assertEquals("NVDO", rules.propagatesHaltFrom().orElseThrow());
    }
}
