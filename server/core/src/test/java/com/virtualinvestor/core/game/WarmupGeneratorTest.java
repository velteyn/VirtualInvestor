package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.instrument.SeriesSymbols;
import com.virtualinvestor.core.market.Bar;
import com.virtualinvestor.core.pricing.OptionPricing;
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

    @Test
    void seriesHistoryMatchesUnderlyingBarCount() {
        InstrumentUniverse universe = universe();
        FundamentalModel model = new FundamentalModel(universe, new RandomStreams(3L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        WarmupGenerator warmup = new WarmupGenerator(new MarketMakerStrategy(0.02, 2.0, 0.001, 500L), STEP_MILLIS, 1_000_000.0);

        Map<String, List<Bar>> spot = warmup.generateHistory(universe, model, 30L, 60L, 1_700_000_000L);
        Map<String, List<Bar>> derived = WarmupGenerator.seriesHistory(universe, spot, 0.001);

        assertTrue(derived.containsKey("AAA-F"));
        assertTrue(derived.containsKey("AAA-C100") || derived.keySet().stream().anyMatch(k -> k.startsWith("AAA-C")));
        for (Map.Entry<String, List<Bar>> entry : derived.entrySet()) {
            assertEquals(30, entry.getValue().size(), entry.getKey());
            for (Bar bar : entry.getValue()) {
                assertTrue(bar.high() >= Math.max(bar.open(), bar.close()), entry.getKey());
                assertTrue(bar.low() <= Math.min(bar.open(), bar.close()), entry.getKey());
                assertEquals(0L, bar.volume());
            }
        }
    }

    @Test
    void seriesHistoryIsDeterministic() {
        InstrumentUniverse universe = universe();
        FundamentalModel first = new FundamentalModel(universe, new RandomStreams(3L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        FundamentalModel second = new FundamentalModel(universe, new RandomStreams(3L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        WarmupGenerator warmup = new WarmupGenerator(new MarketMakerStrategy(0.02, 2.0, 0.001, 500L), STEP_MILLIS, 1_000_000.0);

        Map<String, List<Bar>> a = WarmupGenerator.seriesHistory(universe,
                warmup.generateHistory(universe, first, 30L, 60L, 1_700_000_000L), 0.001);
        Map<String, List<Bar>> b = WarmupGenerator.seriesHistory(universe,
                warmup.generateHistory(universe, second, 30L, 60L, 1_700_000_000L), 0.001);

        assertEquals(a, b);
    }

    @Test
    void putBarsInvertHighAndLow() {
        InstrumentUniverse universe = universe();
        FundamentalModel model = new FundamentalModel(universe, new RandomStreams(3L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        WarmupGenerator warmup = new WarmupGenerator(new MarketMakerStrategy(0.02, 2.0, 0.001, 500L), STEP_MILLIS, 1_000_000.0);

        Map<String, List<Bar>> spot = warmup.generateHistory(universe, model, 30L, 60L, 1_700_000_000L);
        Map<String, List<Bar>> derived = WarmupGenerator.seriesHistory(universe, spot, 0.001);

        String put = derived.keySet().stream().filter(k -> k.startsWith("AAA-P")).sorted().findFirst()
                .orElseThrow();
        double strike = SeriesSymbols.parse(put).orElseThrow().strike();
        Bar firstSpot = spot.get("AAA").get(0);
        Bar firstPut = derived.get(put).get(0);
        double expectedHigh = OptionPricing.put(firstSpot.low(), strike, 0.001, 0.30);
        double expectedLow = OptionPricing.put(firstSpot.high(), strike, 0.001, 0.30);
        assertEquals(expectedHigh, firstPut.high(), 1e-9);
        assertEquals(expectedLow, firstPut.low(), 1e-9);
    }
}
