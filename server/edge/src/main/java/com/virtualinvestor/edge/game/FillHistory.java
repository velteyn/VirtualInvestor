package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.SeriesSymbols;
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
        List<FillRow> settled = new ArrayList<>();
        for (SequencedEvent sequenced : events) {
            if (sequenced.event() instanceof GameEvent.OrderReport report) {
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
            } else if (sequenced.event() instanceof GameEvent.FuturesSettled fill) {
                if (fill.participantId().equals(participantId)) {
                    settled.add(settlementRow(fill.symbol(), fill.quantity(), fill.settlementPrice(),
                            fill.tick()));
                }
            } else if (sequenced.event() instanceof GameEvent.OptionSettled fill) {
                if (fill.participantId().equals(participantId)) {
                    settled.add(settlementRow(fill.symbol(), fill.quantity(), fill.settlementPrice(),
                            fill.tick()));
                }
            }
        }
        List<FillRow> rows = new ArrayList<>();
        for (Accumulator acc : open.values()) {
            double fee = SeriesSymbols.isSeriesSymbol(acc.symbol)
                    ? fees.feeForSeries(acc.filled, acc.filled * acc.price)
                    : fees.feeFor(acc.filled);
            rows.add(new FillRow(acc.orderId, acc.symbol, acc.side, acc.orderType, acc.quantity,
                    acc.filled, acc.price, fee, acc.tick));
        }
        rows.addAll(settled);
        return rows;
    }

    /** A journal row for an expiry/exercise close: no order, no fee, just economics. */
    private static FillRow settlementRow(String symbol, long quantity, double settlementPrice, long tick) {
        return new FillRow("expiry", symbol, "CLOSE", "SETTLEMENT", quantity, quantity, settlementPrice,
                0.0, tick);
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
