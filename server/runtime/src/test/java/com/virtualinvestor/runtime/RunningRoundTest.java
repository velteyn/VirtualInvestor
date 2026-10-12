package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunningRoundTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static LaunchConfig config() {
        return new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC);
    }

    private static RunningRound round(long seed) {
        // A 1-minute step gives a 60-tick session, so continuous trading starts at tick 4.
        return new RunningRound("round-test", seed, config(), MINUTE_MILLIS);
    }

    @Test
    void constructsARunningRoundFromSeedAndConfig() {
        RunningRound round = round(42L);

        assertEquals(
                InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse()).size(),
                round.engines().size(),
                "one book per spot instrument plus every listed series");
        assertEquals(9, round.participants().size(), "human + 5 rivals + 3 institutions");
        assertEquals(1 + 5 + 3, round.participants().size());
        assertEquals(0L, round.tick());
        assertEquals(RoundState.CREATED, round.state());

        round.start();
        assertEquals(RoundState.RUNNING, round.state());
        assertTrue(round.engines().get("NVDO").book().orderCount() > 0, "warm-up seeds the book");
    }

    @Test
    void queuedCommandsAreAppliedInOrderAtTheTopOfTheTick() {
        RunningRound round = round(7L);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        assertEquals(com.virtualinvestor.core.domain.SessionPhase.CONTINUOUS, round.session().phase());
        double farBelow = Math.round(round.fundamentals().fundamental("NVDO") * 0.5 * 100.0) / 100.0;

        round.enqueue(new Command.PlaceOrder("cmd-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, farBelow, 0.0, 0L));
        round.enqueue(new Command.PlaceOrder("cmd-2", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, farBelow, 0.0, 0L));

        List<GameEvent> events = round.step();

        List<String> accepted = events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .filter(report -> report.event() == com.virtualinvestor.core.domain.OrderEventKind.NEW)
                .map(GameEvent.OrderReport::orderId)
                .toList();
        assertEquals(List.of("cmd-1", "cmd-2"), accepted.subList(0, 2),
                "queued commands apply first, in enqueue order");
        assertEquals(0, round.pendingCommands());
    }

    @Test
    void sameSeedProducesIdenticalEventStream() {
        List<GameEvent> first = run(99L, 30);
        List<GameEvent> second = run(99L, 30);
        assertEquals(first, second);
    }

    @Test
    void differentSeedsDiverge() {
        assertNotEquals(run(1L, 30), run(2L, 30));
    }

    private static List<GameEvent> run(long seed, int ticks) {
        RunningRound round = round(seed);
        round.start();
        List<GameEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            all.addAll(round.step());
        }
        return all;
    }
}
