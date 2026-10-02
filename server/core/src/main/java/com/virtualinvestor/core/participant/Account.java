package com.virtualinvestor.core.participant;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cash, positions and P&L for one participant. Fills are applied through {@link #applyFill},
 * which maintains the average entry price, realizes profit when a position is reduced or
 * flipped, and moves cash. Equity is cash plus the mark-to-market value of open positions.
 */
public final class Account {

    private final String participantId;
    private final Map<String, Position> positions = new LinkedHashMap<>();

    private double cash;
    private double realizedPnl;

    public Account(String participantId, double openingCash) {
        this.participantId = Objects.requireNonNull(participantId, "participantId");
        if (openingCash < 0.0) {
            throw new IllegalArgumentException("openingCash must not be negative");
        }
        this.cash = openingCash;
    }

    public String participantId() {
        return participantId;
    }

    public double cash() {
        return cash;
    }

    public double realizedPnl() {
        return realizedPnl;
    }

    public Position position(String ticker) {
        return positions.getOrDefault(ticker, Position.flat(ticker));
    }

    public Map<String, Position> positions() {
        return Collections.unmodifiableMap(positions);
    }

    /** Cash available to open new positions. Margin-aware buying power lands in task 8.5. */
    public double buyingPower() {
        return cash;
    }

    /** Apply an execution: move cash, update the position and realize P&L where applicable. */
    public void applyFill(String ticker, OrderSide side, long quantity, double price) {
        if (quantity <= 0L) {
            throw new IllegalArgumentException("fill quantity must be positive");
        }
        long signed = side == OrderSide.BUY ? quantity : -quantity;
        Position current = position(ticker);
        long netQty = current.netQty();
        double avgPrice = current.avgPrice();

        long newNetQty;
        double newAvgPrice;
        if (netQty == 0L || (netQty > 0L) == (signed > 0L)) {
            long existing = Math.abs(netQty);
            long added = Math.abs(signed);
            newNetQty = netQty + signed;
            newAvgPrice = (avgPrice * existing + price * added) / (existing + added);
        } else {
            long existing = Math.abs(netQty);
            long closing = Math.abs(signed);
            boolean longPosition = netQty > 0L;
            long closed = Math.min(existing, closing);
            realizedPnl += (longPosition ? (price - avgPrice) : (avgPrice - price)) * closed;
            newNetQty = netQty + signed;
            if (newNetQty == 0L) {
                newAvgPrice = 0.0;
            } else if (Math.abs(newNetQty) > existing - closed) {
                newAvgPrice = price; // flipped: the remainder entered at this price
            } else {
                newAvgPrice = avgPrice;
            }
        }

        cash += -signed * price;
        if (newNetQty == 0L) {
            positions.remove(ticker);
        } else {
            positions.put(ticker, new Position(ticker, newNetQty, newAvgPrice));
        }
    }

    /** Unrealized P&L across open positions at the supplied mark prices. */
    public double unrealizedPnl(Map<String, Double> marks) {
        double total = 0.0;
        for (Position position : positions.values()) {
            Double mark = marks.get(position.ticker());
            if (mark != null) {
                total += position.unrealized(mark);
            }
        }
        return total;
    }

    /** Mark-to-market equity: cash plus the value of open positions. */
    public double equity(Map<String, Double> marks) {
        double total = cash;
        for (Position position : positions.values()) {
            Double mark = marks.get(position.ticker());
            if (mark != null) {
                total += position.marketValue(mark);
            }
        }
        return total;
    }
}
