package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.ParticipantType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Realistic-liquidity 1.2: tiered refresh reposts only changed levels. */
class QuoteRefreshTest {

    private static BotRunner runner() {
        return new BotRunner(
                new BotParticipant("m1", "MARK", ParticipantType.RIVAL_BOT, 100_000.0),
                null,
                new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 3, 2.0),
                10L,
                0.01,
                0L);
    }

    private static List<GameEvent> act(BotRunner runner, InstrumentEngine engine, long tick) {
        return runner.act(engine, 100.0, 0.0, 0.0, 0L, tick, 100_000.0, 0.0, null, fill -> {
        });
    }

    @Test
    void unchangedLevelsEmitNothingOverAQuietWindow() {
        BotRunner runner = runner();
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> first = act(runner, engine, 0L);
        assertTrue(first.size() > 0, "the first refresh posts the ladder");

        // Touch-only ticks with an unmoved market repost nothing.
        assertEquals(0, act(runner, engine, 10L).size());
        // Deeper tiers come due on their own cadence but match too.
        assertEquals(0, act(runner, engine, 20L).size());
        assertEquals(0, act(runner, engine, 30L).size());
        assertEquals(6, engine.book().orderCount(), "the full ladder still rests");
    }

    @Test
    void movedMarketReposts() {
        BotRunner runner = runner();
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        act(runner, engine, 0L);

        List<GameEvent> moved = runner.act(engine, 105.0, 0.0, 0.0, 0L, 10L, 100_000.0, 0.0, null, fill -> {
        });

        assertTrue(moved.size() > 0, "a moved fundamental reposts the touch");
    }
}
