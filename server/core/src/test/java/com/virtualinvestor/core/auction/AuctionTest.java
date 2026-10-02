package com.virtualinvestor.core.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionTest {

    private static AuctionOrder buy(String id, long qty, double limit) {
        return new AuctionOrder(id, "p-" + id, OrderSide.BUY, qty, limit, false);
    }

    private static AuctionOrder sell(String id, long qty, double limit) {
        return new AuctionOrder(id, "p-" + id, OrderSide.SELL, qty, limit, false);
    }

    @Test
    void clearsAtVolumeMaximizingPrice() {
        Auction auction = new Auction();
        auction.submit(buy("b1", 100, 100.50));
        auction.submit(buy("b2", 200, 100.00));
        auction.submit(new AuctionOrder("b3", "p-b3", OrderSide.BUY, 50, 0.0, true));
        auction.submit(sell("s1", 150, 99.50));
        auction.submit(sell("s2", 100, 100.00));
        auction.submit(sell("s3", 50, 100.50));

        AuctionResult result = auction.clear(100.00);

        assertEquals(100.00, result.clearingPrice(), 0.0);
        assertEquals(250, result.matchedVolume());
        long buyerVolume = volumeOn(result.fills(), OrderSide.BUY);
        long sellerVolume = volumeOn(result.fills(), OrderSide.SELL);
        assertEquals(250, buyerVolume);
        assertEquals(250, sellerVolume);
    }

    @Test
    void emptyAuctionTradesNothing() {
        AuctionResult result = new Auction().clear(100.00);
        assertTrue(result.fills().isEmpty());
        assertEquals(0, result.matchedVolume());
        assertEquals(100.00, result.clearingPrice(), 0.0);
    }

    @Test
    void oneSidedAuctionTradesNothing() {
        Auction auction = new Auction();
        auction.submit(buy("b1", 100, 101.00));
        auction.submit(buy("b2", 50, 100.50));

        AuctionResult result = auction.clear(100.00);
        assertEquals(0, result.matchedVolume());
        assertTrue(result.fills().isEmpty());
    }

    @Test
    void largerSideAllocatedProRata() {
        Auction auction = new Auction();
        auction.submit(buy("b1", 100, 100.00));
        auction.submit(buy("b2", 100, 100.00));
        auction.submit(buy("b3", 100, 100.00));
        auction.submit(sell("s1", 150, 100.00));

        AuctionResult result = auction.clear(100.00);

        assertEquals(150, result.matchedVolume());
        assertEquals(150, volumeOn(result.fills(), OrderSide.BUY));
        assertEquals(150, volumeOn(result.fills(), OrderSide.SELL));
        for (AuctionFill fill : result.fills()) {
            assertTrue(fill.quantity() > 0L);
        }
    }

    private static long volumeOn(List<AuctionFill> fills, OrderSide side) {
        long total = 0L;
        for (AuctionFill fill : fills) {
            if (fill.side() == side) {
                total += fill.quantity();
            }
        }
        return total;
    }
}
