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
}
