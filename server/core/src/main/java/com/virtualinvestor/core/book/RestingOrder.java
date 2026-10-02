package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.Objects;

/**
 * A live order resting in (or passing through) the book. Mutable because fills and modifies
 * change its remaining quantity, price and priority in place while the book holds references
 * to it in price-ordered queues.
 */
public final class RestingOrder {

    private final String id;
    private final String participantId;
    private final String symbol;
    private final OrderSide side;

    private long quantity;
    private double price;
    private long remaining;
    private long sequence;

    public RestingOrder(String id, String participantId, String symbol, OrderSide side, double price, long quantity) {
        this.id = Objects.requireNonNull(id, "id");
        this.participantId = Objects.requireNonNull(participantId, "participantId");
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.side = Objects.requireNonNull(side, "side");
        this.price = price;
        this.quantity = quantity;
        this.remaining = quantity;
    }

    public String id() {
        return id;
    }

    public String participantId() {
        return participantId;
    }

    public String symbol() {
        return symbol;
    }

    public OrderSide side() {
        return side;
    }

    public double price() {
        return price;
    }

    public long quantity() {
        return quantity;
    }

    public long remaining() {
        return remaining;
    }

    public long filled() {
        return quantity - remaining;
    }

    public long sequence() {
        return sequence;
    }

    void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    void setPrice(double price) {
        this.price = price;
    }

    void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    void setSequence(long sequence) {
        this.sequence = sequence;
    }

    void fill(long filledQuantity) {
        if (filledQuantity < 0L || filledQuantity > remaining) {
            throw new IllegalArgumentException("cannot fill " + filledQuantity + " of " + remaining);
        }
        this.remaining -= filledQuantity;
    }
}
