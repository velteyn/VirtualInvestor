package com.virtualinvestor.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class FuturesMarkTest {

    @Test
    void lastTradeIsTheMark() {
        assertEquals(645.0, SeriesMarks.futuresMark(OptionalDouble.of(645.0), 640.0));
    }

    @Test
    void untradedSeriesMarksToSpot() {
        assertEquals(640.0, SeriesMarks.futuresMark(OptionalDouble.empty(), 640.0));
        assertEquals(640.0, SeriesMarks.futuresReference(640.0));
    }

    @Test
    void marksAreDeterministic() {
        assertEquals(
                SeriesMarks.futuresMark(OptionalDouble.empty(), 640.0),
                SeriesMarks.futuresMark(OptionalDouble.empty(), 640.0));
    }

    @Test
    void invalidSpotRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SeriesMarks.futuresMark(OptionalDouble.empty(), -1.0));
    }
}
