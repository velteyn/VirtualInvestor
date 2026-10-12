package com.virtualinvestor.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.SeriesLister;
import org.junit.jupiter.api.Test;

class MarketRulesTest {

    private static Instrument spot() {
        return new Instrument("NVDO", "Nvido", "Semis", InstrumentType.EQUITY,
                1_000L, 0.9, 0.01, 1L, 640.0, 0.4, 1.0, 1.0);
    }

    private static Instrument future() {
        return new Instrument("NVDO-F", "Nvido Future", "Semis", InstrumentType.FUTURE,
                0L, 1.0, 0.01, 1L, 640.0, 0.4, 1.0, 1.0);
    }

    @Test
    void spotRulesCarryNoHaltPropagation() {
        MarketRules rules = MarketRules.forInstrument(spot());
        assertEquals(0.01, rules.tickSize());
        assertEquals(1L, rules.quantityGrid());
        assertFalse(rules.isSeries());
        assertTrue(rules.propagatesHaltFrom().isEmpty());
    }

    @Test
    void seriesRulesInheritGridsAndNameUnderlying() {
        MarketRules rules = MarketRules.forSeries(future(), RealismPreset.REALISTIC);
        assertEquals(0.01, rules.tickSize());
        assertEquals(1L, rules.quantityGrid());
        assertTrue(rules.isSeries());
        assertEquals("NVDO", rules.propagatesHaltFrom().orElseThrow());
    }

    @Test
    void seriesMarginsFloorAtCanonDefault() {
        MarketRules arcade = MarketRules.forSeries(future(), RealismPreset.ARCADE);
        assertEquals(
                Math.max(SeriesLister.DEFAULT_INITIAL_MARGIN,
                        RealismPreset.ARCADE.initialMarginFraction()),
                arcade.initialMarginFraction());
        assertEquals(RealismPreset.ARCADE.maintenanceMarginFraction(), arcade.maintenanceMarginFraction());

        MarketRules realistic = MarketRules.forSeries(future(), RealismPreset.REALISTIC);
        assertEquals(RealismPreset.REALISTIC.initialMarginFraction(), realistic.initialMarginFraction());
    }

    @Test
    void nonSeriesRejected() {
        assertThrows(IllegalArgumentException.class, () -> MarketRules.forSeries(spot(), RealismPreset.REALISTIC));
    }
}
