package com.virtualinvestor.core.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.instrument.SeriesSymbols.SeriesKind;
import com.virtualinvestor.core.instrument.SeriesSymbols.SeriesTerms;
import org.junit.jupiter.api.Test;

class SeriesSymbolsTest {

    @Test
    void futureSymbolRoundTrips() {
        assertEquals("NVDO-F", SeriesSymbols.futureSymbol("NVDO"));
        SeriesTerms terms = SeriesSymbols.parse("NVDO-F").orElseThrow();
        assertEquals(SeriesKind.FUTURE, terms.kind());
        assertEquals("NVDO", terms.underlying());
        assertEquals(1.0, terms.multiplier());
    }

    @Test
    void optionSymbolsRoundTrip() {
        assertEquals("NVDO-C640", SeriesSymbols.callSymbol("NVDO", 640.0));
        assertEquals("NVDO-P640.5", SeriesSymbols.putSymbol("NVDO", 640.5));

        SeriesTerms call = SeriesSymbols.parse("NVDO-C640").orElseThrow();
        assertEquals(SeriesKind.CALL, call.kind());
        assertEquals("NVDO", call.underlying());
        assertEquals(640.0, call.strike());

        SeriesTerms put = SeriesSymbols.parse("NVDO-P640.5").orElseThrow();
        assertEquals(SeriesKind.PUT, put.kind());
        assertEquals(640.5, put.strike());
    }

    @Test
    void spotTickersAreNotSeries() {
        assertTrue(SeriesSymbols.parse("NVDO").isEmpty());
        assertTrue(SeriesSymbols.parse("VNDX").isEmpty());
        assertTrue(SeriesSymbols.parse("NOPE").isEmpty());
        assertTrue(SeriesSymbols.parse(null).isEmpty());
        assertTrue(SeriesSymbols.parse("NVDO-X640").isEmpty());
        assertTrue(SeriesSymbols.parse("NVDO-C").isEmpty());
        assertTrue(SeriesSymbols.parse("nvdo-f").isEmpty());
    }

    @Test
    void invalidBuildersRejected() {
        assertThrows(IllegalArgumentException.class, () -> SeriesSymbols.futureSymbol(""));
        assertThrows(IllegalArgumentException.class, () -> SeriesSymbols.futureSymbol("nvdo"));
        assertThrows(IllegalArgumentException.class, () -> SeriesSymbols.callSymbol("NVDO", 0.0));
        assertThrows(IllegalArgumentException.class, () -> SeriesSymbols.putSymbol("NVDO", -5.0));
    }
}
