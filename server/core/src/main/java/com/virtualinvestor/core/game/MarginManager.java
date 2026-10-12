package com.virtualinvestor.core.game;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.SeriesSymbols;
import com.virtualinvestor.core.participant.Account;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects margin calls and liquidates. Equity is cash plus mark-to-market position value;
 * when it falls below the maintenance fraction of position value, the participant is called
 * and positions are closed most-levered-first until the requirement is restored, so the
 * loss cannot grow further.
 */
public final class MarginManager {

    private final double maintenanceMarginFraction;

    public MarginManager(double maintenanceMarginFraction) {
        if (maintenanceMarginFraction <= 0.0 || maintenanceMarginFraction >= 1.0) {
            throw new IllegalArgumentException("maintenanceMarginFraction must be within (0, 1)");
        }
        this.maintenanceMarginFraction = maintenanceMarginFraction;
    }

    public MarginCall check(Participant participant, Map<String, Double> marks) {
        Account account = participant.account();
        double equity = account.equity(marks);
        double positionValue = positionValue(account, marks);
        double requirement = positionValue * maintenanceMarginFraction;
        if (equity < requirement) {
            return new MarginCall(participant.id(), equity, requirement);
        }
        return null;
    }

    /**
     * If a margin call is due, liquidate every position, most-levered-first, and return
     * the resulting events. Full flattening (not partial relief) preserves the tuned
     * round dynamics; the ordering only makes the most dangerous exposure close first.
     */
    public List<GameEvent> enforce(Participant participant, Map<String, Double> marks, long tick) {
        MarginCall call = check(participant, marks);
        if (call == null) {
            return List.of();
        }
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.MarginCallIssued(call.participantId(), call.equity(), call.requirement(), tick));
        for (Position position : byExposureDesc(participant, marks)) {
            events.add(closePosition(participant, position.ticker(), marks, tick));
        }
        return List.copyOf(events);
    }

    /** Positions ordered by absolute exposure, ties by ticker for determinism. */
    static List<Position> byExposureDesc(Participant participant, Map<String, Double> marks) {
        List<Position> ordered = new ArrayList<>(participant.account().positions().values());
        ordered.sort(Comparator.comparingDouble(
                        (Position position) -> Math.abs(position.netQty() * mark(marks, position)))
                .reversed()
                .thenComparing(Position::ticker));
        return List.copyOf(ordered);
    }

    /** Flatten every position at its mark, realizing the loss. */
    public static List<GameEvent> forceClose(Participant participant, Map<String, Double> marks, long tick) {
        List<GameEvent> events = new ArrayList<>();
        for (Position position : new ArrayList<>(participant.account().positions().values())) {
            events.add(closePosition(participant, position.ticker(), marks, tick));
        }
        return List.copyOf(events);
    }

    /** Close one position at its mark through the path matching its kind. */
    static GameEvent.LiquidationExecuted closePosition(Participant participant, String ticker,
            Map<String, Double> marks, long tick) {
        Account account = participant.account();
        Position position = account.position(ticker);
        double mark = mark(marks, position);
        OrderSide side = position.netQty() > 0L ? OrderSide.SELL : OrderSide.BUY;
        long quantity = Math.abs(position.netQty());
        Optional<SeriesSymbols.SeriesTerms> series = SeriesSymbols.parse(ticker);
        if (series.isPresent()
                && series.get().kind() == SeriesSymbols.SeriesKind.FUTURE) {
            account.applyFuturesFill(ticker, side, quantity, mark, 0.0);
        } else if (series.isPresent()) {
            account.applyOptionFill(ticker, side, quantity, mark);
        } else {
            account.applyFill(ticker, side, quantity, mark);
        }
        return new GameEvent.LiquidationExecuted(participant.id(), ticker, quantity, mark, tick);
    }

    private static double mark(Map<String, Double> marks, Position position) {
        return marks.getOrDefault(position.ticker(), position.avgPrice());
    }

    private static double positionValue(Account account, Map<String, Double> marks) {
        double total = 0.0;
        for (Position position : account.positions().values()) {
            double mark = marks.getOrDefault(position.ticker(), position.avgPrice());
            total += Math.abs(position.netQty() * mark);
        }
        return total;
    }
}
