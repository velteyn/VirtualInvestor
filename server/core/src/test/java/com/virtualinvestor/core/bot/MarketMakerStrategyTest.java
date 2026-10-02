package com.virtualinvestor.core.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketMakerStrategyTest {

    private final MarketMakerStrategy maker = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L);

    @Test
    void spreadWidensWithVolatility() {
        Quote calm = maker.quote(100.0, 0.0, 0L);
        Quote fearful = maker.quote(100.0, 0.5, 0L);

        assertEquals(0.04, calm.spread(), 1e-9);
        assertTrue(fearful.spread() > calm.spread());
        assertEquals(0.04 * (1.0 + 2.0 * 0.5), fearful.spread(), 1e-9);
    }

    @Test
    void positiveInventorySkewsQuotesDown() {
        Quote flat = maker.quote(100.0, 0.0, 0L);
        Quote longInventory = maker.quote(100.0, 0.0, 100L);

        assertTrue(longInventory.mid() < flat.mid(), "long inventory should lower the mid");
        assertEquals(flat.mid() - 0.001 * 100.0, longInventory.mid(), 1e-9);
    }

    @Test
    void negativeInventorySkewsQuotesUp() {
        Quote flat = maker.quote(100.0, 0.0, 0L);
        Quote shortInventory = maker.quote(100.0, 0.0, -100L);

        assertTrue(shortInventory.mid() > flat.mid(), "short inventory should raise the mid");
    }

    @Test
    void quoteStraddlesTheFundamentalWhenFlat() {
        Quote quote = maker.quote(100.0, 0.0, 0L);
        assertEquals(100.0, quote.mid(), 1e-9);
        assertTrue(quote.bidPrice() < 100.0);
        assertTrue(quote.askPrice() > 100.0);
    }
}
