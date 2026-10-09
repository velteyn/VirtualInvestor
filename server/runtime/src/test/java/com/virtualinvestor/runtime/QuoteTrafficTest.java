package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.ParticipantType;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Realistic-liquidity 2.3: deepening the ladder 1→5 levels grows order-event
 * volume by less than 2x over an identical fundamental path (quadratic tier
 * cadences plus diffed reposts).
 */
class QuoteTrafficTest {

    private static long drive(int levels, long seed) {
        MarketMakerStrategy maker = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, levels, 2.0);
        BotRunner runner = new BotRunner(
                new BotParticipant("m", "MARK", ParticipantType.RIVAL_BOT, 100_000.0),
                null, maker, 10L, 0.01, 0L);
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        Random rng = new Random(seed);
        double fundamental = 100.0;
        long total = 0L;
        for (long tick = 0L; tick < 300L; tick++) {
            fundamental += rng.nextGaussian() * 0.05;
            List<GameEvent> events = runner.act(engine, fundamental, 0.0, 0.0, 0L, tick, 100_000.0, 0.0,
                    null, fill -> {
                    });
            total += events.size();
        }
        return total;
    }

    @Test
    void fiveDeepCostsLessThanTwiceSingleLevel() {
        long single = drive(1, 7L);
        long deep = drive(5, 7L);

        assertTrue(single > 0, "the baseline must emit");
        assertTrue(deep < 2.0 * single, "5-deep must stay under 2x single-level, got " + deep + "/" + single);
    }
}
