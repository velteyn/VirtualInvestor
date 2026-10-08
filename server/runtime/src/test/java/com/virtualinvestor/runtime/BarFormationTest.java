package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Live bars: trade prints aggregate into 60-tick windows closed as BarClosed events. */
class BarFormationTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound round(long seed) {
        // A 1-minute step gives a 60-tick session; run past two bar windows (120 ticks).
        return new RunningRound("round-bars", seed,
                new LaunchConfig(100_000.0, 180L, RealismPreset.REALISTIC), MINUTE_MILLIS);
    }

    @Test
    void printsAggregateIntoClosedBarsOnTheHistoryAxis() {
        RunningRound round = round(7L);
        round.start();
        long historyEnd = round.history().get("NVDO").get(59).timeSec();

        List<GameEvent.BarClosed> bars = new ArrayList<>();
        for (int i = 0; i < 175; i++) {
            for (GameEvent event : round.step()) {
                if (event instanceof GameEvent.BarClosed bar) {
                    bars.add(bar);
                }
            }
        }

        assertTrue(bars.size() > 0, "trading across a window boundary must close bars");
        for (GameEvent.BarClosed bar : bars) {
            assertTrue(bar.barTime() > historyEnd,
                    "live bars continue the history axis, not overlap it: " + bar);
            assertTrue(bar.high() >= bar.open() && bar.high() >= bar.close(), "high bounds the bar: " + bar);
            assertTrue(bar.low() <= bar.open() && bar.low() <= bar.close(), "low bounds the bar: " + bar);
            assertTrue(bar.volume() > 0, "closed bars reflect real prints: " + bar);
        }
        long nvdoBars = bars.stream().filter(bar -> bar.symbol().equals("NVDO")).count();
        assertTrue(nvdoBars >= 1, "the most liquid name closes at least one bar, got " + bars.size());
    }

    @Test
    void barFormationIsDeterministic() {
        List<GameEvent> first = run(99L);
        List<GameEvent> second = run(99L);
        assertEquals(first, second);
    }

    private static List<GameEvent> run(long seed) {
        RunningRound round = round(seed);
        round.start();
        List<GameEvent> all = new ArrayList<>();
        for (int i = 0; i < 175; i++) {
            all.addAll(round.step());
        }
        return all;
    }
}
