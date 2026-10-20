package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.bot.Quote;
import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.fundamental.VolatilityState;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import org.junit.jupiter.api.Test;

class VolatilityStateTest {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;

    private static FundamentalModel model(VolatilityState volatility) {
        Instrument instrument = new Instrument("HIGH1", "Nvido-like", "Semis", InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, 0.30, 1.8, 0.0);
        FundamentalModel model = new FundamentalModel(
                InstrumentUniverse.of(List.of(instrument)), new RandomStreams(1L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        model.setVolatilityState(volatility);
        return model;
    }

    @Test
    void stateBumpsAndDecaysTowardCalm() {
        VolatilityState state = new VolatilityState(0.5);
        assertEquals(1.0, state.multiplier("HIGH1"), 1e-9);

        state.bump("HIGH1", 0.5);
        assertEquals(1.5, state.multiplier("HIGH1"), 1e-9);

        state.step();
        assertEquals(1.25, state.multiplier("HIGH1"), 1e-9);
        assertTrue(state.multiplier("HIGH1") > 1.0);
    }

    @Test
    void newsElevatesVolatilityForAffectedNames() {
        VolatilityState state = new VolatilityState(0.99);
        FundamentalModel model = model(state);
        NewsEngine engine = new NewsEngine(model, List.of(new PlannedNews(
                new NewsItem("n1", NewsSource.THE_WIRE, ReliabilityTier.WIRE, NewsScope.COMPANY, "HIGH1", "Semis",
                        "Rumour", "body", 1.0, 0.5, 100L, NewsStage.RUMOR, 10L, 0.5, 100L, true, null, null),
                40L, true, 1.0)));
        engine.setVolatilityState(state);

        engine.advance(10L);

        assertEquals(1.5, state.multiplier("HIGH1"), 1e-9, "news should elevate volatility");
    }

    @Test
    void raisedVolatilityWidensMarketMakerSpread() {
        MarketMakerStrategy maker = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L);
        Quote calm = maker.quote(100.0, 0.0, 0L);
        Quote shocked = maker.quote(100.0, 0.5, 0L);
        assertTrue(shocked.spread() > calm.spread());
    }

    @Test
    void raisedVolatilityIncreasesRealizedVolatility() {
        VolatilityState calm = new VolatilityState(1.0);
        double calmRealized = realizedVolatility(model(calm), 2000);

        VolatilityState shocked = new VolatilityState(1.0);
        shocked.bump("HIGH1", 2.0); // multiplier 3.0
        double shockedRealized = realizedVolatility(model(shocked), 2000);

        assertTrue(shockedRealized > calmRealized * 1.5,
                () -> calmRealized + " vs " + shockedRealized);
    }

    private static double realizedVolatility(FundamentalModel model, int steps) {
        double sum = 0.0;
        double sumSquares = 0.0;
        for (int i = 0; i < steps; i++) {
            model.step();
            double r = model.lastReturn("HIGH1");
            sum += r;
            sumSquares += r * r;
        }
        double mean = sum / steps;
        return Math.sqrt(sumSquares / steps - mean * mean);
    }
}
