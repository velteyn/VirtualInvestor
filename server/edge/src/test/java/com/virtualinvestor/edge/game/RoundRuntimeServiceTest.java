package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.ws.MarketBroadcaster;
import com.virtualinvestor.runtime.RunningRound;
import org.junit.jupiter.api.Test;

/** Group 6.1: create starts warm-up + loop; pause and resume act on the running world. */
class RoundRuntimeServiceTest {

    @Test
    void startBeginsARunningRoundWithWarmupAndStreaming() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RoundRuntimeService runtime = new RoundRuntimeService(new RoundBroadcaster(broadcaster));

        RunningRound round = runtime.start("r1", 61L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false);

        assertEquals(RoundState.RUNNING, round.state());
        assertTrue(round.history().size() > 0, "warm-up generated history");
        assertTrue(round.engines().get("NVDO").book().orderCount() > 0, "the book is seeded");
        assertTrue(runtime.round("r1").isPresent());
    }

    @Test
    void pauseAndResumeChangeTheLifecycleState() {
        RoundRuntimeService runtime = new RoundRuntimeService(new RoundBroadcaster(new MarketBroadcaster()));
        runtime.start("r2", 62L, new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false);

        runtime.pause("r2");
        assertEquals(RoundState.PAUSED, runtime.round("r2").orElseThrow().state());

        runtime.resume("r2");
        assertEquals(RoundState.RUNNING, runtime.round("r2").orElseThrow().state());
    }

    @Test
    void submittedCommandsReachTheRound() {
        RoundRuntimeService runtime = new RoundRuntimeService(new RoundBroadcaster(new MarketBroadcaster()));
        RunningRound round = runtime.start("r3", 63L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false);

        runtime.submit("r3", "human",
                new com.virtualinvestor.edge.wire.WireMessage.PlaceOrderCommand("t1", "NVDO", "BUY", "LIMIT", 10L, 1.0, 0.0),
                round.tick());

        assertEquals(1, round.pendingCommands(), "the command is queued on the round");
    }
}
