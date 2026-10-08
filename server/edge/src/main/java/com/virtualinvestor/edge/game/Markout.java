package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.SequencedEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-fill markouts folded from the journal (realistic-liquidity 3.1): each fill
 * joined to the first print on its symbol at or after fill tick + horizon, so
 * execution quality (paid spread, adverse move) is measurable without touching
 * the hot path. Pure fold — identical on live and replayed streams.
 */
public final class Markout {

    public record MarkoutRow(String orderId, String symbol, String side, double fillPrice, double markPrice,
            double markout, long fillTick, long markTick) {
    }

    private Markout() {
    }

    public static List<MarkoutRow> fold(List<SequencedEvent> events, String participantId, long horizonTicks) {
        Map<String, List<Print>> prints = new LinkedHashMap<>();
        for (SequencedEvent sequenced : events) {
            if (sequenced.event() instanceof GameEvent.TradePrinted trade) {
                prints.computeIfAbsent(trade.symbol(), symbol -> new ArrayList<>())
                        .add(new Print(trade.tick(), trade.price()));
            }
        }
        prints.values().forEach(list -> list.sort(Comparator.comparingLong(Print::tick)));

        List<MarkoutRow> rows = new ArrayList<>();
        for (SequencedEvent sequenced : events) {
            if (!(sequenced.event() instanceof GameEvent.OrderReport report)) {
                continue;
            }
            if (!report.participantId().equals(participantId)
                    || (report.event() != com.virtualinvestor.core.domain.OrderEventKind.FILL
                            && report.event() != com.virtualinvestor.core.domain.OrderEventKind.PARTIAL_FILL)) {
                continue;
            }
            Print mark = firstAtOrAfter(prints.getOrDefault(report.symbol(), List.of()),
                    report.tick() + horizonTicks);
            if (mark == null) {
                continue;
            }
            double sign = report.side() == com.virtualinvestor.core.domain.OrderSide.BUY ? 1.0 : -1.0;
            rows.add(new MarkoutRow(report.orderId(), report.symbol(), report.side().name(), report.price(),
                    mark.price(), sign * (mark.price() - report.price()), report.tick(), mark.tick()));
        }
        return rows;
    }

    private record Print(long tick, double price) {
    }

    private static Print firstAtOrAfter(List<Print> prints, long tick) {
        Print best = null;
        for (Print print : prints) {
            if (print.tick() >= tick && (best == null || print.tick() < best.tick())) {
                best = print;
            }
        }
        return best;
    }
}
