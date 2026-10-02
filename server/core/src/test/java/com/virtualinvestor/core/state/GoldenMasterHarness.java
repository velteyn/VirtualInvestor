package com.virtualinvestor.core.state;

import com.virtualinvestor.core.clock.WorldClock;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.random.DeterministicRandom;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a fixed-seed scenario through the clock and seeded streams, collecting the event log
 * and its fingerprint. It stands in for the full world until Groups 3-5 replace the synthetic
 * event generation with real market events; the hashing contract is identical.
 */
final class GoldenMasterHarness {

    record Result(long ticks, List<GameEvent> events, String hash) {
    }

    private GoldenMasterHarness() {
    }

    static Result run(long seed, int steps) {
        WorldClock clock = new WorldClock(WorldClock.DEFAULT_STEP_MILLIS, () -> 0L);
        RandomStreams streams = new RandomStreams(seed);
        DeterministicRandom market = streams.stream("market");
        DeterministicRandom flow = streams.stream("flow");
        List<GameEvent> events = new ArrayList<>();

        clock.start();
        for (int i = 0; i < steps; i++) {
            clock.step();
            long tick = clock.tick();
            events.add(new GameEvent.Tick(tick, clock.logicalMillis()));

            double factor = market.nextGaussian() * 0.001;
            events.add(new GameEvent.MarketFactorMoved(0.0, factor, tick));
            if (flow.nextDouble() < 0.2) {
                events.add(new GameEvent.TradePrinted("IDX", 1000.0 * (1.0 + factor), 100L, OrderSide.BUY, tick));
            }
        }
        return new Result(clock.tick(), List.copyOf(events), WorldStateHasher.hash(clock, events));
    }
}
