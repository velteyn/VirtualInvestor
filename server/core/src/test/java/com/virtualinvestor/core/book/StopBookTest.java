package com.virtualinvestor.core.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class StopBookTest {

    @Test
    void buyStopTriggersOnRiseAndConvertsToMarket() {
        StopBook book = new StopBook("ACME");
        book.add(new StopOrder("s1", "p1", "ACME", OrderSide.BUY, OrderType.STOP, 100, 105.0, 0.0));

        assertTrue(book.onLastTradedPrice(104.0).isEmpty(), "must not trigger below the stop");
        assertEquals(1, book.size());

        List<IncomingOrder> triggered = book.onLastTradedPrice(105.0);
        assertEquals(1, triggered.size());
        assertEquals(OrderType.MARKET, triggered.get(0).type());
        assertEquals(0, book.size(), "triggered stop is removed");
        assertTrue(book.onLastTradedPrice(106.0).isEmpty(), "already-triggered stop does not fire twice");
    }

    @Test
    void sellStopTriggersOnFall() {
        StopBook book = new StopBook("ACME");
        book.add(new StopOrder("s1", "p1", "ACME", OrderSide.SELL, OrderType.STOP, 100, 95.0, 0.0));

        assertTrue(book.onLastTradedPrice(96.0).isEmpty());
        List<IncomingOrder> triggered = book.onLastTradedPrice(94.99);
        assertEquals(1, triggered.size());
        assertEquals(OrderSide.SELL, triggered.get(0).side());
    }

    @Test
    void stopLimitConvertsToLimitAtItsLimitPrice() {
        StopBook book = new StopBook("ACME");
        book.add(new StopOrder("s1", "p1", "ACME", OrderSide.BUY, OrderType.STOP_LIMIT, 100, 105.0, 106.0));

        List<IncomingOrder> triggered = book.onLastTradedPrice(105.0);
        assertEquals(1, triggered.size());
        assertEquals(OrderType.LIMIT, triggered.get(0).type());
        assertEquals(106.0, triggered.get(0).limitPrice(), 0.0);
    }

    @Test
    void untradedPriceDoesNotTrigger() {
        StopBook book = new StopBook("ACME");
        book.add(new StopOrder("s1", "p1", "ACME", OrderSide.BUY, OrderType.STOP, 100, 105.0, 0.0));

        assertTrue(book.onLastTradedPrice(100.0).isEmpty());
        assertEquals(1, book.size());
    }

    @Test
    void cancelRemovesUntriggeredStop() {
        StopBook book = new StopBook("ACME");
        book.add(new StopOrder("s1", "p1", "ACME", OrderSide.BUY, OrderType.STOP, 100, 105.0, 0.0));

        book.cancel("s1");
        assertEquals(0, book.size());
        assertTrue(book.onLastTradedPrice(200.0).isEmpty());
    }
}
