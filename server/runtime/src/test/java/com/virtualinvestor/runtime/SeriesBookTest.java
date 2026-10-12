package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 3.3: every listed series trades on its own book at round setup. */
class SeriesBookTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-series", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        return round;
    }

    @Test
    void seriesOrdersRestMatchAndPrintOnTheirOwnBook() {
        RunningRound round = continuousRound(31L);
        double last = round.marks().get("NVDO-F");

        // Rest a buy on the future, then cross it: self-match proves the series book works.
        round.enqueue(new Command.PlaceOrder("s1", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, last, 0.0, 0L));
        round.step();
        round.enqueue(new Command.PlaceOrder("s2", "human", "NVDO-F",
                OrderSide.SELL, OrderType.LIMIT, 5L, last, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.TradePrinted printed
                && printed.symbol().equals("NVDO-F")
                && printed.quantity() == 5L
                && printed.price() == last));
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.PositionUpdated updated
                && updated.symbol().equals("NVDO-F")
                && updated.participantId().equals("human")));
    }

    @Test
    void seriesFillsDoNotTouchTheUnderlyingPosition() {
        RunningRound round = continuousRound(32L);
        double last = round.marks().get("NVDO-F");

        round.enqueue(new Command.PlaceOrder("s3", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, last, 0.0, 0L));
        round.step();
        round.enqueue(new Command.PlaceOrder("s4", "human", "NVDO-F",
                OrderSide.SELL, OrderType.LIMIT, 5L, last, 0.0, 0L));
        round.step();

        assertEquals(0L, round.participant("human").account().position("NVDO").netQty());
        assertEquals(0.0, round.participant("human").account().realizedFor("NVDO"));
    }

    @Test
    void unknownSeriesIsRejected() {
        RunningRound round = continuousRound(33L);
        round.enqueue(new Command.PlaceOrder("s5", "human", "NOPE-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, 10.0, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.OrderReport rejected
                && rejected.orderId().equals("s5")
                && rejected.event() == OrderEventKind.REJECTED));
    }

    @Test
    void seriesChartsOpenWithWarmupHistory() {
        RunningRound round = continuousRound(34L);

        assertEquals(60, round.history().get("NVDO-F").size());
        assertEquals(60, round.history().get("NVDO").size());
        assertTrue(round.history().containsKey("NVDO-C640"));
        assertTrue(round.history().get("NVDO-C640").stream().allMatch(bar -> bar.volume() == 0L));
    }
}
