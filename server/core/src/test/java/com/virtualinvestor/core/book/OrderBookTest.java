package com.virtualinvestor.core.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private static RestingOrder order(String id, OrderSide side, double price, long quantity) {
        return new RestingOrder(id, "p-" + id, "ACME", side, price, quantity);
    }

    @Test
    void timePriorityWithinALevel() {
        OrderBook book = new OrderBook("ACME");
        book.add(order("a", OrderSide.BUY, 100.00, 10));
        book.add(order("b", OrderSide.BUY, 100.00, 20));

        assertEquals("a", book.peekBest(OrderSide.BUY).id());
        assertEquals(2, book.depth(OrderSide.BUY, 1).get(0).orderCount());
    }

    @Test
    void pricePriorityOrdersTheSides() {
        OrderBook book = new OrderBook("ACME");
        book.add(order("b1", OrderSide.BUY, 99.50, 10));
        book.add(order("b2", OrderSide.BUY, 100.00, 10));
        book.add(order("b3", OrderSide.BUY, 99.00, 10));
        book.add(order("a1", OrderSide.SELL, 101.00, 10));
        book.add(order("a2", OrderSide.SELL, 100.50, 10));

        assertEquals(100.00, book.bestBidPrice(), 0.0);
        assertEquals(100.50, book.bestAskPrice(), 0.0);

        List<DepthLevel> bids = book.depth(OrderSide.BUY, 3);
        assertEquals(List.of(100.00, 99.50, 99.00), bids.stream().map(DepthLevel::price).toList());
        List<DepthLevel> asks = book.depth(OrderSide.SELL, 3);
        assertEquals(List.of(100.50, 101.00), asks.stream().map(DepthLevel::price).toList());
    }

    @Test
    void depthAggregatesQuantityPerLevel() {
        OrderBook book = new OrderBook("ACME");
        book.add(order("a", OrderSide.BUY, 100.00, 10));
        book.add(order("b", OrderSide.BUY, 100.00, 25));
        book.add(order("c", OrderSide.BUY, 99.50, 5));

        List<DepthLevel> depth = book.depth(OrderSide.BUY, 2);
        assertEquals(35, depth.get(0).quantity());
        assertEquals(5, depth.get(1).quantity());
        assertEquals(40, book.aggregateQuantity(OrderSide.BUY, 2));
    }

    @Test
    void cancelRemovesOrderAndEmptiesLevel() {
        OrderBook book = new OrderBook("ACME");
        book.add(order("a", OrderSide.BUY, 100.00, 10));
        RestingOrder cancelled = book.cancel("a");

        assertEquals("a", cancelled.id());
        assertNull(book.peekBest(OrderSide.BUY));
        assertEquals(0, book.orderCount());
        assertNull(book.cancel("missing"));
    }

    @Test
    void modifyDownKeepsPriorityAndModifyUpLosesIt() {
        OrderBook book = new OrderBook("ACME");
        book.add(order("a", OrderSide.BUY, 100.00, 100));
        book.add(order("b", OrderSide.BUY, 100.00, 50));

        book.modify("a", 100.00, 40);
        assertEquals("a", book.peekBest(OrderSide.BUY).id(), "reducing quantity keeps priority");
        assertEquals(40, book.get("a").remaining());

        book.modify("a", 100.00, 500);
        assertEquals("b", book.peekBest(OrderSide.BUY).id(), "raising quantity loses priority");
    }

    @Test
    void modifyPriceLosesPriority() {
        OrderBook book = new OrderBook("ACME");
        book.add(order("a", OrderSide.BUY, 100.00, 10));
        book.add(order("b", OrderSide.BUY, 100.00, 10));

        book.modify("a", 99.75, 10);

        assertEquals(100.00, book.bestBidPrice(), 0.0);
        assertEquals("b", book.peekBest(OrderSide.BUY).id());
        assertTrue(book.get("a").sequence() > book.get("b").sequence());
    }
}
