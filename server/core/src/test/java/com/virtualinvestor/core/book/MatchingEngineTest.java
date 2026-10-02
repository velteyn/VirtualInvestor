package com.virtualinvestor.core.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    private static RestingOrder ask(String id, double price, long quantity) {
        return new RestingOrder(id, "maker-" + id, "ACME", OrderSide.SELL, price, quantity);
    }

    private static IncomingOrder incoming(String id, OrderSide side, OrderType type, long quantity, double price) {
        return new IncomingOrder(id, "taker", "ACME", side, type, quantity, price);
    }

    @Test
    void marketOrderFullyFillsAgainstRestingLiquidity() {
        OrderBook book = new OrderBook("ACME");
        book.add(ask("a1", 100.00, 30));
        book.add(ask("a2", 100.50, 40));

        MatchResult result = engine.match(book, incoming("t1", OrderSide.BUY, OrderType.MARKET, 50, 0.0));

        assertFalse(result.rejected());
        assertEquals(50, result.filledQuantity());
        assertFalse(result.rested());
        assertEquals(2, result.fills().size());
        assertEquals(100.00, result.fills().get(0).price(), 0.0);
        assertEquals(100.50, result.fills().get(1).price(), 0.0);
        assertEquals(20, book.get("a2").remaining());
    }

    @Test
    void limitOrderPartiallyFillsAndRestsRemainder() {
        OrderBook book = new OrderBook("ACME");
        book.add(ask("a1", 100.00, 30));

        MatchResult result = engine.match(book, incoming("t1", OrderSide.BUY, OrderType.LIMIT, 50, 100.00));

        assertEquals(30, result.filledQuantity());
        assertTrue(result.rested());
        assertNull(result.rejection());
        RestingOrder resting = book.peekBest(OrderSide.BUY);
        assertEquals("t1", resting.id());
        assertEquals(20, resting.remaining());
    }

    @Test
    void nonCrossingLimitOrderRestsWithoutFilling() {
        OrderBook book = new OrderBook("ACME");
        book.add(ask("a1", 101.00, 30));

        MatchResult result = engine.match(book, incoming("t1", OrderSide.BUY, OrderType.LIMIT, 50, 100.00));

        assertEquals(0, result.filledQuantity());
        assertTrue(result.rested());
        assertTrue(result.fills().isEmpty());
    }

    @Test
    void marketOrderWithoutLiquidityIsRejected() {
        OrderBook book = new OrderBook("ACME");

        MatchResult result = engine.match(book, incoming("t1", OrderSide.BUY, OrderType.MARKET, 50, 0.0));

        assertTrue(result.rejected());
        assertEquals("no liquidity for market order", result.rejection());
        assertEquals(0, result.filledQuantity());
    }

    @Test
    void marketOrderRemainderIsCancelledNotRested() {
        OrderBook book = new OrderBook("ACME");
        book.add(ask("a1", 100.00, 20));

        MatchResult result = engine.match(book, incoming("t1", OrderSide.BUY, OrderType.MARKET, 50, 0.0));

        assertFalse(result.rejected());
        assertEquals(20, result.filledQuantity());
        assertFalse(result.rested());
        assertNull(book.peekBest(OrderSide.BUY));
    }
}
