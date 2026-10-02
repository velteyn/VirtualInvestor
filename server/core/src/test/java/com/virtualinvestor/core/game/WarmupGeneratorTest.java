package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.market.Bar;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WarmupGeneratorTest {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;

    private static InstrumentUniverse universe() {
        return InstrumentUniverse.of(List.of(
                new Instrument("AAA", "Alpha", "Tech", InstrumentType.EQUITY, 1_000_000L, 1.0, 0.01, 1L, 100.0, 0.30, 1.2, 0.1),
                new Instrument("BBB", "Beta", "Tech", InstrumentType.EQUITY, 1_000_000L, 1.0, 0.01, 1L, 50.0, 0.40, 1.5, 0.2)));
    }

    @Test
    void historyBarsAreGeneratedAndConsistent() {
        InstrumentUniverse universe = universe();
        FundamentalModel model = new FundamentalModel(universe, new RandomStreams(3L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        WarmupGenerator warmup = new WarmupGenerator(new MarketMakerStrategy(0.02, 2.0, 0.001, 500L), STEP_MILLIS, 1_000_000.0);

        Map<String, List<Bar>> history = warmup.generateHistory(universe, model, 30L, 60L, 1_700_000_000L);

        assertEquals(2, history.size());
        for (List<Bar> bars : history.values()) {
            assertEquals(30, bars.size());
            for (Bar bar : bars) {
                assertTrue(bar.high() >= Math.max(bar.open(), bar.close()));
                assertTrue(bar.low() <= Math.min(bar.open(), bar.close()));
                assertTrue(bar.volume() >= 0L);
            }
            for (int i = 1; i < bars.size(); i++) {
                assertTrue(bars.get(i).timeSec() > bars.get(i - 1).timeSec());
            }
        }
    }

    @Test
    void booksAreNonEmptyAfterSeeding() {
        InstrumentUniverse universe = universe();
        FundamentalModel model = new FundamentalModel(universe, new RandomStreams(3L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        WarmupGenerator warmup = new WarmupGenerator(new MarketMakerStrategy(0.02, 2.0, 0.001, 500L), STEP_MILLIS, 1_000_000.0);

        List<InstrumentEngine> engines = List.of(
                new InstrumentEngine("AAA", 100.0), new InstrumentEngine("BBB", 50.0));
        warmup.seedBooks(engines, model);

        for (InstrumentEngine engine : engines) {
            assertEquals(2, engine.book().orderCount());
            assertTrue(engine.book().bestBidPrice() < engine.book().bestAskPrice());
        }
    }
}
