package com.virtualinvestor.core.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SymbolSearchTest {

    private final SymbolSearch search = new SymbolSearch(InstrumentUniverse.defaultUniverse());

    @Test
    void exactTickerRanksFirst() {
        List<Instrument> results = search.search("PEAR", 5);
        assertEquals("PEAR", results.get(0).ticker());
        assertEquals("Consumer Tech", results.get(0).sector());
        assertEquals(InstrumentType.EQUITY, results.get(0).type());
    }

    @Test
    void prefixMatchesByName() {
        List<Instrument> results = search.search("Cyber", 5);
        assertTrue(results.stream().anyMatch(instrument -> instrument.ticker().equals("CYBR")));
    }

    @Test
    void caseInsensitiveAndTrimmed() {
        assertEquals("PEAR", search.search("  pear  ", 1).get(0).ticker());
    }

    @Test
    void substringMatches() {
        List<Instrument> results = search.search("energy", 5);
        assertTrue(results.stream().anyMatch(instrument -> instrument.ticker().equals("SOLR")));
    }

    @Test
    void unknownQueryReturnsEmpty() {
        assertTrue(search.search("zzzzzz", 5).isEmpty());
    }

    @Test
    void respectsLimit() {
        assertEquals(3, search.search("", 3).size());
    }

    @Test
    void chainListsFutureThenCallsThenPuts() {
        SymbolSearch withSeries = new SymbolSearch(
                InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse()));
        List<Instrument> chain = withSeries.chain("NVDO");

        assertEquals("NVDO-F", chain.get(0).ticker());
        assertEquals(1 + 2 * (1 + 2 * 3), chain.size());
        List<Double> callStrikes = chain.stream()
                .filter(i -> i.ticker().contains("-C"))
                .map(i -> SeriesSymbols.parse(i.ticker()).orElseThrow().strike())
                .toList();
        List<Double> sorted = callStrikes.stream().sorted().toList();
        assertEquals(sorted, callStrikes);
    }

    @Test
    void chainIsEmptyWithoutSeries() {
        assertTrue(search.chain("NVDO").isEmpty());
        SymbolSearch withSeries = new SymbolSearch(
                InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse()));
        assertTrue(withSeries.chain("NOPE").isEmpty());
        assertTrue(withSeries.chain(null).isEmpty());
    }

    @Test
    void searchFindsSeriesByPrefix() {
        SymbolSearch withSeries = new SymbolSearch(
                InstrumentUniverse.withSeries(InstrumentUniverse.defaultUniverse()));
        List<Instrument> results = withSeries.search("NVDO-", 30);
        assertTrue(results.stream().anyMatch(i -> i.ticker().equals("NVDO-F")));
    }
}
