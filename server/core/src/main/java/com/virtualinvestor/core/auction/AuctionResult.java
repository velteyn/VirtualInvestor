package com.virtualinvestor.core.auction;

import java.util.List;

/** The outcome of an auction: the clearing price, matched volume and per-order allocations. */
public record AuctionResult(
        double clearingPrice,
        long matchedVolume,
        List<AuctionFill> fills) {

    public AuctionResult {
        fills = List.copyOf(fills);
    }

    public boolean traded() {
        return matchedVolume > 0L;
    }
}
