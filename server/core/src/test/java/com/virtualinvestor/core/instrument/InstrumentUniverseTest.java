package com.virtualinvestor.core.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InstrumentUniverseTest {

    private static final Set<String> REAL_COMPANY_NAMES = Set.of(
            "apple", "microsoft", "nvidia", "meta", "tesla", "amazon", "google", "alphabet",
            "netflix", "amd", "intel", "cisco", "paypal", "salesforce", "adobe", "oracle",
            "broadcom", "qualcomm", "uber", "lyft", "shopify", "square", "block", "coinbase",
            "snowflake", "palantir", "twitter", "spotify", "airbnb", "stripe");

    private static final Set<String> REAL_TICKERS = Set.of(
            "AAPL", "MSFT", "NVDA", "META", "TSLA", "AMZN", "GOOG", "GOOGL", "NFLX", "AMD",
            "INTC", "CSCO", "PYPL", "CRM", "ADBE", "ORCL", "AVGO", "QCOM", "UBER", "LYFT",
            "SHOP", "SQ", "COIN", "SNOW", "PLTR");

    @Test
    void loadsUniverseFromClasspath() {
        InstrumentUniverse universe = InstrumentUniverse.defaultUniverse();

        assertEquals(21, universe.size());
        assertEquals(20, universe.equities().size());
        assertTrue(universe.index().isPresent(), "expected one index");
        assertEquals("VNDX", universe.index().orElseThrow().ticker());
        assertTrue(universe.find("PEAR").isPresent());
        assertTrue(universe.find("NOPE").isEmpty());
    }

    @Test
    void tickersAreUnique() {
        List<String> tickers = InstrumentUniverse.defaultUniverse().all().stream()
                .map(Instrument::ticker)
                .toList();
        assertEquals(tickers.size(), tickers.stream().collect(Collectors.toSet()).size());
    }

    @Test
    void noRealCompanyNamesOrTickers() {
        for (Instrument instrument : InstrumentUniverse.defaultUniverse().all()) {
            String name = instrument.name().toLowerCase(Locale.ROOT);
            for (String real : REAL_COMPANY_NAMES) {
                assertFalse(name.contains(real), () -> instrument.ticker() + " name references real company " + real);
            }
            assertFalse(REAL_TICKERS.contains(instrument.ticker()),
                    () -> "ticker references real company: " + instrument.ticker());
        }
    }

    @Test
    void withSeriesAddsFindableDerivativeBooks() {
        InstrumentUniverse universe = InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse());

        assertTrue(universe.size() > 21);
        assertTrue(universe.find("NVDO-F").isPresent());
        assertTrue(universe.find("NOPE-F").isEmpty());
        assertTrue(universe.find("NOPE").isEmpty());
    }

    @Test
    void equitiesExcludeSeries() {
        InstrumentUniverse universe = InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse());

        assertEquals(20, universe.equities().size());
        assertTrue(universe.equities().stream().noneMatch(Instrument::isSeries));
        assertEquals(21, universe.spots().size());
    }

    @Test
    void withSeriesIsDeterministic() {
        InstrumentUniverse first = InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse());
        InstrumentUniverse second = InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse());

        assertEquals(first.all(), second.all());
    }
}
