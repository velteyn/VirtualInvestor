package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches incoming orders against an {@link OrderBook} at the resting (maker) price.
 * MARKET orders sweep available liquidity and cancel any remainder; LIMIT orders cross only
 * at acceptable prices and rest any remainder. Maker orders fill in price-time priority.
 */
public final class MatchingEngine {

    public MatchResult match(OrderBook book, IncomingOrder incoming) {
        if (incoming.type() != OrderType.MARKET && incoming.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException(
                    "MatchingEngine handles MARKET and LIMIT; got " + incoming.type());
        }
        if (incoming.quantity() <= 0L) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        List<Fill> fills = new ArrayList<>();
        long remaining = incoming.quantity();
        OrderSide opposite = incoming.side() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;

        while (remaining > 0L) {
            RestingOrder best = book.peekBest(opposite);
            if (best == null) {
                break;
            }
            if (incoming.type() == OrderType.LIMIT && !crosses(incoming.side(), incoming.limitPrice(), best.price())) {
                break;
            }
            long quantity = Math.min(remaining, best.remaining());
            long makerQuantity = best.quantity();
            best.fill(quantity);
            long makerRemainingAfter = best.remaining();
            fills.add(new Fill(best.id(), incoming.id(), best.price(), quantity, makerQuantity, makerRemainingAfter));
            remaining -= quantity;
            if (makerRemainingAfter == 0L) {
                book.removeOrder(best);
            }
        }

        long filled = incoming.quantity() - remaining;
        if (remaining == 0L) {
            return new MatchResult(fills, filled, false, null);
        }
        if (incoming.type() == OrderType.MARKET) {
            String rejection = fills.isEmpty() ? "no liquidity for market order" : null;
            return new MatchResult(fills, filled, false, rejection);
        }
        RestingOrder rested = new RestingOrder(
                incoming.id(), incoming.participantId(), incoming.symbol(), incoming.side(), incoming.limitPrice(), remaining);
        book.add(rested);
        return new MatchResult(fills, filled, true, null);
    }

    private static boolean crosses(OrderSide side, double limitPrice, double bookPrice) {
        return side == OrderSide.BUY ? bookPrice <= limitPrice : bookPrice >= limitPrice;
    }
}
