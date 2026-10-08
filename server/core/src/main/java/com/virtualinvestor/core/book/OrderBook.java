package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Price-time-priority limit order book. Bids iterate highest-first, asks lowest-first, and
 * orders at a price fill in the order they arrived. Modify keeps priority only when it does
 * not raise the total quantity and leaves the price unchanged; otherwise the order re-enters
 * at the back of its level, which is the venue behaviour a trader expects.
 */
public final class OrderBook {

    private final String symbol;
    private final NavigableMap<Double, ArrayDeque<RestingOrder>> bids =
            new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Double, ArrayDeque<RestingOrder>> asks = new TreeMap<>();
    private final Map<String, RestingOrder> byId = new LinkedHashMap<>();

    private long sequence = 0L;

    public OrderBook(String symbol) {
        this.symbol = Objects.requireNonNull(symbol, "symbol");
    }

    public String symbol() {
        return symbol;
    }

    public long add(RestingOrder order) {
        if (byId.containsKey(order.id())) {
            throw new IllegalArgumentException("duplicate order id: " + order.id());
        }
        order.setSequence(++sequence);
        byId.put(order.id(), order);
        attach(order);
        return order.sequence();
    }

    public RestingOrder cancel(String orderId) {
        RestingOrder order = byId.remove(orderId);
        if (order == null) {
            return null;
        }
        detach(order, order.price());
        return order;
    }

    public void modify(String orderId, double newPrice, long newQuantity) {
        RestingOrder order = byId.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("unknown order: " + orderId);
        }
        long filled = order.filled();
        long quantity = Math.max(newQuantity, filled);
        double oldPrice = order.price();
        boolean priceChanged = Double.compare(newPrice, oldPrice) != 0;
        boolean losesPriority = priceChanged || quantity > order.quantity();

        if (losesPriority) {
            detach(order, oldPrice);
        }
        order.setQuantity(quantity);
        order.setRemaining(quantity - filled);
        if (losesPriority) {
            order.setPrice(newPrice);
            order.setSequence(++sequence);
            attach(order);
        }
    }

    public RestingOrder get(String orderId) {
        return byId.get(orderId);
    }

    /** All resting orders, insertion order preserved (used for reconnect snapshots). */
    public List<RestingOrder> orders() {
        return List.copyOf(byId.values());
    }

    public int orderCount() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** Best resting order on the side, or null when that side is empty. */
    public RestingOrder peekBest(OrderSide side) {
        Map.Entry<Double, ArrayDeque<RestingOrder>> entry = map(side).firstEntry();
        return entry == null ? null : entry.getValue().peekFirst();
    }

    public Double bestBidPrice() {
        Map.Entry<Double, ArrayDeque<RestingOrder>> entry = bids.firstEntry();
        return entry == null ? null : entry.getKey();
    }

    public Double bestAskPrice() {
        Map.Entry<Double, ArrayDeque<RestingOrder>> entry = asks.firstEntry();
        return entry == null ? null : entry.getKey();
    }

    /** Remove an order that has been fully filled by the matching engine. */
    public void removeOrder(RestingOrder order) {
        detach(order, order.price());
        byId.remove(order.id());
    }

    /** Aggregated depth on one side, best price first, up to {@code levels} levels. */
    public List<DepthLevel> depth(OrderSide side, int levels) {
        if (levels <= 0) {
            return List.of();
        }
        List<DepthLevel> out = new ArrayList<>();
        for (Map.Entry<Double, ArrayDeque<RestingOrder>> entry : map(side).entrySet()) {
            if (out.size() >= levels) {
                break;
            }
            long quantity = 0L;
            for (RestingOrder order : entry.getValue()) {
                quantity += order.remaining();
            }
            out.add(new DepthLevel(entry.getKey(), quantity, entry.getValue().size()));
        }
        return List.copyOf(out);
    }

    /** Total resting quantity on one side across the best {@code levels} levels. */
    public long aggregateQuantity(OrderSide side, int levels) {
        long total = 0L;
        for (DepthLevel level : depth(side, levels)) {
            total += level.quantity();
        }
        return total;
    }

    private NavigableMap<Double, ArrayDeque<RestingOrder>> map(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    private void attach(RestingOrder order) {
        map(order.side()).computeIfAbsent(order.price(), price -> new ArrayDeque<>()).addLast(order);
    }

    private void detach(RestingOrder order, double price) {
        ArrayDeque<RestingOrder> queue = map(order.side()).get(price);
        if (queue == null) {
            return;
        }
        queue.remove(order);
        if (queue.isEmpty()) {
            map(order.side()).remove(price);
        }
    }
}
