package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.SequencedEvent;
import com.virtualinvestor.runtime.FeeSchedule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-participant fill history folded from the round journal (portfolio-tab 2.1).
 * One row per order that filled: cumulative quantity, last price, and the fee on
 * the cumulative fill under the given schedule. Pure fold — identical on live
 * and replayed streams, which is what task 2.2 verifies.
 */
public final class FillHistory {

    public record FillRow(String orderId, String symbol, String side, String orderType, long quantity,
            long filledQuantity, double price, double fee, long tick) {
    }

    private FillHistory() {
    }

    public static List<FillRow> fold(List<SequencedEvent> events, String participantId, FeeSchedule fees) {
        Map<String, Accumulator> open = new LinkedHashMap<>();
        for (SequencedEvent sequenced : events) {
            if (!(sequenced.event() instanceof GameEvent.OrderReport report)) {
                continue;
            }
            if (!report.participantId().equals(participantId)) {
                continue;
            }
            if (report.event() != com.virtualinvestor.core.domain.OrderEventKind.FILL
                    && report.event() != com.virtualinvestor.core.domain.OrderEventKind.PARTIAL_FILL) {
                continue;
            }
            open.compute(report.orderId(), (id, acc) -> acc == null
                    ? new Accumulator(report)
                    : acc.update(report));
        }
        List<FillRow> rows = new ArrayList<>();
        for (Accumulator acc : open.values()) {
            rows.add(new FillRow(acc.orderId, acc.symbol, acc.side, acc.orderType, acc.quantity,
                    acc.filled, acc.price, fees.feeFor(acc.filled), acc.tick));
        }
        return rows;
    }

    private static final class Accumulator {
        final String orderId;
        final String symbol;
        final String side;
        final String orderType;
        final long quantity;
        long filled;
        double price;
        long tick;

        Accumulator(GameEvent.OrderReport report) {
            this.orderId = report.orderId();
            this.symbol = report.symbol();
            this.side = report.side().name();
            this.orderType = report.orderType().name();
            this.quantity = report.quantity();
            update(report);
        }

        Accumulator update(GameEvent.OrderReport report) {
            this.filled = report.filledQuantity();
            this.price = report.price();
            this.tick = report.tick();
            return this;
        }
    }
}
