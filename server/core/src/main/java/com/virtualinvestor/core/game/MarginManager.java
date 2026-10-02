package com.virtualinvestor.core.game;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.Account;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects margin calls and liquidates. Equity is cash plus mark-to-market position value;
 * when it falls below the maintenance fraction of position value, the participant is called
 * and every position is force-closed at its mark so the loss cannot grow further.
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

    /** If a margin call is due, liquidate all positions and return the resulting events. */
    public List<GameEvent> enforce(Participant participant, Map<String, Double> marks, long tick) {
        MarginCall call = check(participant, marks);
        if (call == null) {
            return List.of();
        }
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.MarginCallIssued(call.participantId(), call.equity(), call.requirement(), tick));
        events.addAll(forceClose(participant, marks, tick));
        return List.copyOf(events);
    }

    /** Flatten every position at its mark, realizing the loss. */
    public static List<GameEvent> forceClose(Participant participant, Map<String, Double> marks, long tick) {
        Account account = participant.account();
        List<GameEvent> events = new ArrayList<>();
        for (Position position : new ArrayList<>(account.positions().values())) {
            double mark = marks.getOrDefault(position.ticker(), position.avgPrice());
            OrderSide side = position.netQty() > 0L ? OrderSide.SELL : OrderSide.BUY;
            long quantity = Math.abs(position.netQty());
            account.applyFill(position.ticker(), side, quantity, mark);
            events.add(new GameEvent.LiquidationExecuted(participant.id(), position.ticker(), quantity, mark, tick));
        }
        return List.copyOf(events);
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
