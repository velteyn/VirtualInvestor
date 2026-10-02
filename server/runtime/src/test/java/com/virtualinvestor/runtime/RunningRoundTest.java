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
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunningRoundTest {

    private static LaunchConfig config() {
        return new LaunchConfig(100_000.0, 30L, RealismPreset.REALISTIC);
    }

    private static RunningRound round(long seed) {
        return new RunningRound("round-test", seed, config());
    }

    @Test
    void constructsARunningRoundFromSeedAndConfig() {
        RunningRound round = round(42L);

        assertEquals(21, round.engines().size());
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
        double farBelow = round.fundamentals().fundamental("NVDO") * 0.5;

        round.enqueue(new Command.PlaceOrder("cmd-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, farBelow, 0.0, 0L));
        round.enqueue(new Command.PlaceOrder("cmd-2", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, farBelow, 0.0, 0L));

        List<GameEvent> events = round.step();

        List<String> accepted = events.stream()
                .filter(GameEvent.OrderAccepted.class::isInstance)
                .map(event -> ((GameEvent.OrderAccepted) event).orderId())
                .toList();
        assertEquals(List.of("cmd-1", "cmd-2"), accepted.subList(0, 2),
                "queued commands apply first, in enqueue order");
        assertEquals(0, round.pendingCommands());
    }

    @Test
    void sameSeedProducesIdenticalEventStream() {
        List<GameEvent> first = run(99L, 150);
        List<GameEvent> second = run(99L, 150);
        assertEquals(first, second);
    }

    @Test
    void differentSeedsDiverge() {
        assertNotEquals(run(1L, 100), run(2L, 100));
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
