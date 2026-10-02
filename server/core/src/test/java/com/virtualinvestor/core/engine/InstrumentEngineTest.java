package com.virtualinvestor.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.StopOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentEngineTest {

    private static IncomingOrder order(String id, String participant, OrderSide side, OrderType type, long qty, double price) {
        return new IncomingOrder(id, participant, "ACME", side, type, qty, price);
    }

    private static List<String> typeNames(List<GameEvent> events) {
        return events.stream().map(event -> event.getClass().getSimpleName()).toList();
    }

    @Test
    void restingOrderEmitsAcceptedThenRested() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> events = engine.submit(order("a1", "maker", OrderSide.SELL, OrderType.LIMIT, 50, 100.0), 1L);

        assertEquals(List.of("OrderAccepted", "OrderRested"), typeNames(events));
    }

    @Test
    void marketOrderFullyFillingEmitsLifecycleAndTradePrint() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("a1", "maker", OrderSide.SELL, OrderType.LIMIT, 50, 100.0), 1L);

        List<GameEvent> events = engine.submit(order("t1", "taker", OrderSide.BUY, OrderType.MARKET, 50, 0.0), 2L);

        assertEquals(List.of("OrderAccepted", "TradePrinted", "OrderFilled", "OrderFilled"), typeNames(events));
        GameEvent.TradePrinted print = (GameEvent.TradePrinted) events.get(1);
        assertEquals(100.0, print.price(), 0.0);
        assertEquals(50, print.quantity());
        assertEquals("a1", ((GameEvent.OrderFilled) events.get(2)).orderId());
        assertEquals("t1", ((GameEvent.OrderFilled) events.get(3)).orderId());
    }

    @Test
    void partialFillThenCancelledRemainder() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("a2", "maker", OrderSide.SELL, OrderType.LIMIT, 30, 101.0), 1L);

        List<GameEvent> events = engine.submit(order("t2", "taker", OrderSide.BUY, OrderType.MARKET, 50, 0.0), 2L);

        assertEquals(List.of("OrderAccepted", "TradePrinted", "OrderFilled", "OrderPartiallyFilled", "OrderCancelled"),
                typeNames(events));
        assertEquals(30, ((GameEvent.OrderPartiallyFilled) events.get(3)).filledQuantity());
    }

    @Test
    void marketOrderWithoutLiquidityIsRejected() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> events = engine.submit(order("t3", "taker", OrderSide.BUY, OrderType.MARKET, 10, 0.0), 1L);

        assertEquals(List.of("OrderAccepted", "OrderRejected"), typeNames(events));
        assertEquals("no liquidity for market order", ((GameEvent.OrderRejected) events.get(1)).reason());
    }

    @Test
    void crossingPriceTriggersStopAndEmitsItsLifecycle() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submit(order("a3", "maker", OrderSide.SELL, OrderType.LIMIT, 20, 102.0), 1L);
        engine.stops().add(new StopOrder("s1", "stopper", "ACME", OrderSide.BUY, OrderType.STOP, 10, 102.0, 0.0));

        List<GameEvent> events = engine.submit(order("t4", "taker", OrderSide.BUY, OrderType.MARKET, 5, 0.0), 2L);
        List<String> names = typeNames(events);

        int triggeredAt = names.indexOf("OrderTriggered");
        assertTrue(triggeredAt > 0, () -> "expected a triggered stop in " + names);
        long triggeredOrders = events.stream().filter(GameEvent.OrderTriggered.class::isInstance).count();
        assertEquals(1, triggeredOrders);
        assertTrue(names.contains("TradePrinted"));
        assertEquals(0, engine.stops().size(), "triggered stop is consumed");
    }
}
