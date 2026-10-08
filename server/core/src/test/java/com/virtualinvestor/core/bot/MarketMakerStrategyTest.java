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

    @Test
    void ladderGrowsSmallAtTouchLargeBehind() {
        MarketMakerStrategy deep = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 3, 2.0);
        Quote quote = deep.quote(100.0, 0.0, 0L);

        assertEquals(3, quote.bidLevels().size());
        assertEquals(3, quote.askLevels().size());
        assertEquals(500L, quote.bidSize());
        assertEquals(1000L, quote.bidLevels().get(1).size());
        assertEquals(2000L, quote.bidLevels().get(2).size());
        assertTrue(quote.bidLevels().get(0).price() > quote.bidLevels().get(1).price());
        assertTrue(quote.askLevels().get(0).price() < quote.askLevels().get(1).price());
        assertEquals(quote.bidPrice(), quote.bidLevels().get(0).price(), 1e-9);
    }

    @Test
    void toxicityShrinksSizesAndWidensSpread() {
        MarketMakerStrategy nervous = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 1, 2.0, 1.0, 4.0);
        Quote calm = nervous.quote(100.0, 0.0, 0L, 0.0);
        Quote stressed = nervous.quote(100.0, 0.0, 0L, 1.0);

        assertEquals(500L, calm.bidSize());
        assertTrue(stressed.bidSize() < calm.bidSize(), "stress shrinks size");
        assertTrue(stressed.spread() > calm.spread(), "stress widens spread");
    }

    @Test
    void toxicityAtToleranceWithdrawsFully() {
        MarketMakerStrategy nervous = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 2, 2.0, 1.0, 4.0);
        Quote gone = nervous.quote(100.0, 0.0, 0L, 4.0);

        assertTrue(gone.bidLevels().stream().allMatch(level -> level.size() == 0L));
        assertTrue(gone.askLevels().stream().allMatch(level -> level.size() == 0L));
    }

    @Test
    void fadedStressRequotesFully() {
        MarketMakerStrategy nervous = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 1, 2.0, 1.0, 4.0);
        nervous.quote(100.0, 0.0, 0L, 4.0);
        Quote calm = nervous.quote(100.0, 0.0, 0L, 0.0);

        assertEquals(500L, calm.bidSize(), "faded stress requotes at full size");
    }

    @Test
    void singleLevelPreservesClassicQuote() {
        Quote quote = maker.quote(100.0, 0.0, 0L);

        assertEquals(1, quote.bidLevels().size());
        assertEquals(1, quote.askLevels().size());
        assertEquals(500L, quote.bidSize());
        assertEquals(500L, quote.askSize());
    }
}
