package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.ParticipantType;
import org.junit.jupiter.api.Test;

/** Dead-market-makers: sub-tick references must skip rungs, never post bad prices. */
class BotRunnerInvalidQuoteTest {

    private static BotRunner maker() {
        return new BotRunner(
                new BotParticipant("m1", "MARK", ParticipantType.RIVAL_BOT, 100_000.0),
                null,
                new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 3, 2.0),
                10L,
                0.01,
                0L);
    }

    @Test
    void subTickReferencePostsNoInvalidBids() {
        BotRunner runner = maker();
        InstrumentEngine engine = new InstrumentEngine("NVDO-P700", 0.01);

        runner.quoteSeries(engine, 0.01, 0L, 0L, 100_000.0, fill -> {
        });

        Double bid = engine.book().bestBidPrice();
        assertTrue(bid == null || bid > 0.0, "bid must be positive, was " + bid);
        engine.book().orders().forEach(order ->
                assertTrue(order.price() > 0.0, "resting price must be positive, was " + order.price()));
    }

    @Test
    void nanReferencePostsNothing() {
        BotRunner runner = maker();
        InstrumentEngine engine = new InstrumentEngine("NVDO-P700", 0.01);

        runner.quoteSeries(engine, Double.NaN, 0L, 0L, 100_000.0, fill -> {
        });

        assertEquals(0, engine.book().orderCount(), "NaN reference posts nothing");
    }
}
