package com.virtualinvestor.core.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeriesListerTest {

    private final InstrumentUniverse base = InstrumentUniverse.defaultUniverse();

    @Test
    void listsOneFuturePerTradeablePlusIndex() {
        List<Instrument> series = SeriesLister.list(base);
        long futures = series.stream().filter(i -> i.type() == InstrumentType.FUTURE).count();
        assertEquals(base.equities().size() + 1, futures);
        assertTrue(series.stream().anyMatch(i -> i.ticker().equals("VNDX-F")));
        assertTrue(series.stream().anyMatch(i -> i.ticker().equals("NVDO-F")));
    }

    @Test
    void listsAtmPlusThreeStrikesEachSidePerEquity() {
        List<Instrument> nvdo = SeriesLister.list(base).stream()
                .filter(i -> i.type() == InstrumentType.OPTION
                        && SeriesSymbols.parse(i.ticker()).orElseThrow().underlying().equals("NVDO"))
                .toList();
        long calls = nvdo.stream()
                .filter(i -> SeriesSymbols.parse(i.ticker()).orElseThrow().kind()
                        == SeriesSymbols.SeriesKind.CALL)
                .count();
        assertEquals(2 * (1 + 2 * SeriesLister.STRIKES_EACH_SIDE), nvdo.size());
        assertEquals(1 + 2 * SeriesLister.STRIKES_EACH_SIDE, calls);
    }

    @Test
    void listingIsDeterministic() {
        assertEquals(SeriesLister.list(base), SeriesLister.list(base));
    }

    @Test
    void seriesCarryUnderlyingMetadata() {
        Instrument future = SeriesLister.list(base).stream()
                .filter(i -> i.ticker().equals("NVDO-F"))
                .findFirst()
                .orElseThrow();
        Instrument spot = base.find("NVDO").orElseThrow();
        assertEquals(spot.sector(), future.sector());
        assertEquals(spot.tickSize(), future.tickSize());
        assertEquals(spot.quantityGrid(), future.quantityGrid());
        assertEquals(spot.referencePrice(), future.referencePrice());
    }
}
