package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.SequencedEvent;
import com.virtualinvestor.runtime.FeeSchedule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Portfolio-tab 2.1: the journal fold is participant-scoped and aggregates per order. */
class FillHistoryTest {

    private static GameEvent.OrderReport report(String orderId, String participant, OrderEventKind event,
            long filled, double price, long tick) {
        return new GameEvent.OrderReport(orderId, participant, "NVDO", OrderSide.BUY, OrderType.MARKET, 10L,
                price, filled, 10L - filled,
                event == OrderEventKind.FILL ? OrderStatus.FILLED : OrderStatus.PARTIAL, event, null, tick);
    }

    @Test
    void fillsAggregatePerOrderForTheRequesterOnly() {
        List<SequencedEvent> events = List.of(
                new SequencedEvent(1L, report("o1", "human", OrderEventKind.PARTIAL_FILL, 4L, 100.0, 5L)),
                new SequencedEvent(2L, report("o2", "rival-1", OrderEventKind.FILL, 10L, 101.0, 5L)),
                new SequencedEvent(3L, report("o1", "human", OrderEventKind.FILL, 10L, 100.5, 6L)),
                new SequencedEvent(4L, new GameEvent.Tick(6L, 600L)));

        List<FillHistory.FillRow> rows = FillHistory.fold(events, "human", FeeSchedule.fixed(0.01, 2.0));

        assertEquals(1, rows.size());
        FillHistory.FillRow row = rows.get(0);
        assertEquals("o1", row.orderId());
        assertEquals(10L, row.filledQuantity());
        assertEquals(100.5, row.price(), 1e-9);
        assertEquals(6L, row.tick());
        assertEquals(2.0, row.fee(), 1e-9, "10 shares at 0.01 binds the 2.00 minimum");
    }

    @Test
    void noFillsMeansNoRows() {
        List<SequencedEvent> events = List.of(
                new SequencedEvent(1L, report("o1", "human", OrderEventKind.REJECTED, 0L, 0.0, 5L)));

        assertTrue(FillHistory.fold(events, "human", FeeSchedule.none()).isEmpty());
    }
}
