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

class FundamentalReversionTest {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;

    private static FundamentalModel model() {
        Instrument instrument = new Instrument("AAA", "Alpha", "Tech", InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, 0.0, 0.0, 0.0);
        return new FundamentalModel(
                InstrumentUniverse.of(List.of(instrument)), new RandomStreams(1L), 0.0, 0.0, STEP_MILLIS, YEAR_MILLIS);
    }

    @Test
    void transientBurstRevertsTowardTheReference() {
        FundamentalModel model = model();
        model.setReversionStrength(0.25);
        model.setOrderFlowImpact(new OrderFlowImpact(0.0, 0.05, 0.9, Map.of("AAA", 1.0)));

        double reference = model.reference("AAA");
        double moved = model.applyFlow("AAA", 100.0);
        assertTrue(moved > reference, "the burst should raise the fundamental");

        // With only transient impact, reversion pulls it back toward the reference.
        for (int i = 0; i < 200; i++) {
            model.step();
        }
        double after = model.fundamental("AAA");
        assertTrue(Math.abs(after - model.reference("AAA")) < Math.abs(moved - reference),
                "the fundamental should move back toward the reference");
    }

    @Test
    void sustainedFlowLeavesAPermanentOffset() {
        FundamentalModel model = model();
        model.setReversionStrength(0.10);
        model.setOrderFlowImpact(new OrderFlowImpact(0.002, 0.0, 0.5, Map.of("AAA", 1.0)));

        double originalReference = model.reference("AAA");
        for (int i = 0; i < 50; i++) {
            model.applyFlow("AAA", 100.0);
        }
        // The permanent impact moves the reference itself, so it survives reversion.
        assertTrue(model.reference("AAA") > originalReference * 1.01,
                "sustained flow should durably raise the reference: "
                        + model.reference("AAA") + " vs " + originalReference);
        assertEquals(model.fundamental("AAA"), model.reference("AAA"), 1e-9,
                "with no transient part, the fundamental settles on the moved reference");
    }

    @Test
    void noFlowWithZeroImpactKeepsFactorsBehaving() {
        // Task 1.3: with impact disabled, the fundamental still responds to factors.
        Instrument instrument = new Instrument("AAA", "Alpha", "Tech", InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, 0.0, 1.0, 0.0);
        FundamentalModel model = new FundamentalModel(
                InstrumentUniverse.of(List.of(instrument)), new RandomStreams(1L), 0.30, 0.0, STEP_MILLIS, YEAR_MILLIS);
        model.setOrderFlowImpact(OrderFlowImpact.inert());

        double start = model.fundamental("AAA");
        for (int i = 0; i < 100; i++) {
            model.step();
        }
        assertTrue(model.fundamental("AAA") != start, "factors still move the fundamental");
        assertEquals(model.fundamental("AAA"), model.reference("AAA"), 1e-9,
                "with no flow and no reversion drift, fundamental tracks the reference");
    }
}
