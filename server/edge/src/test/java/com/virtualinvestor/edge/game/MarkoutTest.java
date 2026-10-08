package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.SequencedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Realistic-liquidity 3.1: markouts join fills to the first print past the horizon. */
class MarkoutTest {

    private static GameEvent.OrderReport fill(String orderId, OrderSide side, double price, long tick) {
        return new GameEvent.OrderReport(orderId, "human", "NVDO", side, OrderType.MARKET, 10L, price, 10L,
                0L, OrderStatus.FILLED, OrderEventKind.FILL, null, tick);
    }

    private static GameEvent.TradePrinted print(double price, long tick) {
        return new GameEvent.TradePrinted("NVDO", price, 5L, OrderSide.BUY, tick);
    }

    @Test
    void favorableAndAdverseMovesSignCorrectly() {
        List<SequencedEvent> events = List.of(
                new SequencedEvent(1L, fill("b1", OrderSide.BUY, 100.0, 10L)),
                new SequencedEvent(2L, print(100.0, 10L)),
                new SequencedEvent(3L, print(101.0, 15L)),
                new SequencedEvent(4L, fill("s1", OrderSide.SELL, 101.0, 20L)),
                new SequencedEvent(5L, print(100.0, 25L)));

        List<Markout.MarkoutRow> rows = Markout.fold(events, "human", 5L);

        assertEquals(2, rows.size());
        // Buy 100, mark 101 five ticks later: +1.
        assertEquals(1.0, rows.get(0).markout(), 1e-9);
        assertEquals(15L, rows.get(0).markTick());
        // Sell 101, mark 100 five ticks later: +1 (favorable for the short).
        assertEquals(1.0, rows.get(1).markout(), 1e-9);
    }

    @Test
    void fillWithoutAHorizonPrintIsSkipped() {
        List<SequencedEvent> events = List.of(
                new SequencedEvent(1L, fill("b1", OrderSide.BUY, 100.0, 90L)),
                new SequencedEvent(2L, print(100.0, 91L)));

        assertEquals(0, Markout.fold(events, "human", 5L).size());
    }

    @Test
    void otherParticipantsAreExcluded() {
        List<SequencedEvent> events = List.of(
                new SequencedEvent(1L, new GameEvent.OrderReport("o1", "rival-1", "NVDO", OrderSide.BUY,
                        OrderType.MARKET, 10L, 100.0, 10L, 0L, OrderStatus.FILLED, OrderEventKind.FILL,
                        null, 10L)),
                new SequencedEvent(2L, print(105.0, 15L)));

        assertEquals(0, Markout.fold(events, "human", 5L).size());
    }
}
