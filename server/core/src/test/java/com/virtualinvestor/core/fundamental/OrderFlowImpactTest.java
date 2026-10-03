package com.virtualinvestor.core.fundamental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderFlowImpactTest {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;

    private static Instrument instrument(String ticker) {
        return new Instrument(ticker, "Test " + ticker, "Tech", InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, 0.05, 1.0, 0.0);
    }

    private static FundamentalModel model(OrderFlowImpact impact) {
        InstrumentUniverse universe = InstrumentUniverse.of(List.of(instrument("LIQ"), instrument("ILLIQ")));
        FundamentalModel model = new FundamentalModel(
                universe, new RandomStreams(1L), 0.0, 0.0, STEP_MILLIS, YEAR_MILLIS);
        model.setOrderFlowImpact(impact);
        return model;
    }

    @Test
    void buyFlowRaisesAndSellFlowLowersTheFundamental() {
        OrderFlowImpact impact = new OrderFlowImpact(0.001, 0.0, 0.5, Map.of("LIQ", 1.0));
        FundamentalModel model = model(impact);

        double start = model.fundamental("LIQ");
        model.applyFlow("LIQ", 100.0);
        assertTrue(model.fundamental("LIQ") > start, "buy flow should raise the fundamental");

        double afterBuy = model.fundamental("LIQ");
        model.applyFlow("LIQ", -200.0);
        assertTrue(model.fundamental("LIQ") < afterBuy, "sell flow should lower the fundamental");
    }

    @Test
    void illiquidNameMovesMoreThanLiquidOne() {
        OrderFlowImpact impact = new OrderFlowImpact(0.001, 0.0, 0.5, Map.of("LIQ", 100.0, "ILLIQ", 1.0));
        FundamentalModel model = model(impact);

        double liqStart = model.fundamental("LIQ");
        double illiqStart = model.fundamental("ILLIQ");
        model.applyFlow("LIQ", 100.0);
        model.applyFlow("ILLIQ", 100.0);

        double liqMove = (model.fundamental("LIQ") - liqStart) / liqStart;
        double illiqMove = (model.fundamental("ILLIQ") - illiqStart) / illiqStart;
        assertTrue(illiqMove > liqMove * 10, () -> "illiquid " + illiqMove + " vs liquid " + liqMove);
    }

    @Test
    void transientOffsetDecays() {
        OrderFlowImpact impact = new OrderFlowImpact(0.0, 0.01, 0.5, Map.of("LIQ", 1.0));
        model(impact);
        impact.apply(100.0, "LIQ", 10.0);
        double first = impact.transientOffset("LIQ");
        impact.apply(100.0, "LIQ", 0.0);
        double second = impact.transientOffset("LIQ");
        assertEquals(first * 0.5, second, 1e-9, "the transient offset should decay by the decay factor");
    }

    @Test
    void inertImpactChangesNothing() {
        OrderFlowImpact impact = OrderFlowImpact.inert();
        FundamentalModel model = model(impact);
        double start = model.fundamental("LIQ");
        model.applyFlow("LIQ", 1_000_000.0);
        assertEquals(start, model.fundamental("LIQ"), 0.0);
    }
}
