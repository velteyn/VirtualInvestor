package com.virtualinvestor.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.StopOrder;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentEngineTest {

    private static IncomingOrder order(
            String id, String participant, OrderSide side, OrderType type, long quantity, double price) {
        return new IncomingOrder(id, participant, "ACME", side, type, quantity, price);
    }

    private static List<OrderEventKind> kinds(List<GameEvent> events) {
        return events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .map(GameEvent.OrderReport::event)
                .toList();
    }

    private static List<String> typeNames(List<GameEvent> events) {
        return events.stream().map(event -> event.getClass().getSimpleName()).toList();
    }

    @Test
    void restingOrderEmitsNewThenRestedReports() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> events = engine.submit(order("a1", "maker", OrderSide.SELL, OrderType.LIMIT, 50, 100.0), 1L);

        assertEquals(List.of(OrderEventKind.NEW, OrderEventKind.RESTED), kinds(events));
        GameEvent.OrderReport rested = (GameEvent.OrderReport) events.get(1);
        assertEquals(OrderSide.SELL, rested.side());
        assertEquals(OrderType.LIMIT, rested.orderType());
        assertEquals(50L, rested.quantity());
        assertEquals(OrderStatus.WORKING, rested.status());
    }

    @Test
    void marketOrderFullyFillingEmitsReportsWithFullContext() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("a1", "maker", OrderSide.SELL, OrderType.LIMIT, 50, 100.0), 1L);

        List<GameEvent> events = engine.submit(order("t1", "taker", OrderSide.BUY, OrderType.MARKET, 50, 0.0), 2L);

        assertEquals(List.of("OrderReport", "TradePrinted", "OrderReport", "OrderReport"), typeNames(events));
        GameEvent.TradePrinted print = (GameEvent.TradePrinted) events.get(1);
        assertEquals(100.0, print.price(), 0.0);
        GameEvent.OrderReport maker = (GameEvent.OrderReport) events.get(2);
        assertEquals("a1", maker.orderId());
        assertEquals(OrderSide.SELL, maker.side());
        assertEquals(OrderStatus.FILLED, maker.status());
        GameEvent.OrderReport taker = (GameEvent.OrderReport) events.get(3);
        assertEquals("t1", taker.orderId());
        assertEquals(OrderSide.BUY, taker.side());
        assertEquals(50L, taker.filledQuantity());
        assertEquals(0L, taker.leavesQuantity());
        assertEquals(OrderStatus.FILLED, taker.status());
    }

    @Test
    void partialFillThenCancelledRemainder() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("a2", "maker", OrderSide.SELL, OrderType.LIMIT, 30, 101.0), 1L);

        List<GameEvent> events = engine.submit(order("t2", "taker", OrderSide.BUY, OrderType.MARKET, 50, 0.0), 2L);

        assertEquals(
                List.of(OrderEventKind.NEW, OrderEventKind.FILL, OrderEventKind.PARTIAL_FILL, OrderEventKind.CANCELLED),
                kinds(events));
        List<GameEvent.OrderReport> reports = events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .toList();
        GameEvent.OrderReport partial = reports.get(2);
        assertEquals(30L, partial.filledQuantity());
        assertEquals(20L, partial.leavesQuantity());
        GameEvent.OrderReport cancelled = reports.get(3);
        assertEquals(OrderStatus.CANCELLED, cancelled.status());
    }

    @Test
    void largeOrderWalksTheLadderLevelByLevel() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("ask1", "maker", OrderSide.SELL, OrderType.LIMIT, 10, 100.0), 1L);
        engine.submit(order("ask2", "maker", OrderSide.SELL, OrderType.LIMIT, 10, 101.0), 1L);
        engine.submit(order("ask3", "maker", OrderSide.SELL, OrderType.LIMIT, 10, 102.0), 1L);

        List<GameEvent> events = engine.submit(order("sweep", "taker", OrderSide.BUY, OrderType.MARKET, 25, 0.0), 2L);

        List<GameEvent.OrderReport> makerReports = events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .filter(r -> !r.orderId().equals("sweep"))
                .toList();
        // ask1 and ask2 fully consumed, ask3 partially filled at the top level.
        assertEquals(List.of(100.0, 101.0, 102.0),
                makerReports.stream().map(GameEvent.OrderReport::price).toList());
        assertEquals(List.of(10L, 10L, 5L),
                makerReports.stream().map(GameEvent.OrderReport::filledQuantity).toList());
        GameEvent.OrderReport taker = events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .filter(r -> r.orderId().equals("sweep"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals(25L, taker.filledQuantity());
        assertEquals(OrderStatus.FILLED, taker.status());
    }

    @Test
    void marketOrderWithoutLiquidityIsRejected() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> events = engine.submit(order("t3", "taker", OrderSide.BUY, OrderType.MARKET, 10, 0.0), 1L);

        assertEquals(List.of(OrderEventKind.NEW, OrderEventKind.REJECTED), kinds(events));
        GameEvent.OrderReport rejected = (GameEvent.OrderReport) events.get(1);
        assertEquals("no liquidity for market order", rejected.reason());
        assertEquals(OrderStatus.REJECTED, rejected.status());
        assertEquals(OrderSide.BUY, rejected.side());
    }

    @Test
    void crossingPriceTriggersStopAndEmitsItsLifecycle() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("a3", "maker", OrderSide.SELL, OrderType.LIMIT, 20, 102.0), 1L);
        engine.stops().add(new StopOrder("s1", "stopper", "ACME", OrderSide.BUY, OrderType.STOP, 10, 102.0, 0.0));

        List<GameEvent> events = engine.submit(order("t4", "taker", OrderSide.BUY, OrderType.MARKET, 5, 0.0), 2L);

        long triggered = events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .filter(report -> report.event() == OrderEventKind.TRIGGERED)
                .count();
        assertEquals(1, triggered, "the stop should trigger once");
        assertTrue(events.stream().anyMatch(GameEvent.TradePrinted.class::isInstance));
        assertEquals(0, engine.stops().size(), "triggered stop is consumed");
    }
}
