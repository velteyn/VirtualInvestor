package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds untriggered stop orders for one instrument and converts them when the last traded
 * price crosses their trigger. A buy stop triggers on a rise to or through its stop price;
 * a sell stop triggers on a fall to or through it. Triggered orders are removed and returned
 * in insertion order as live incoming orders.
 */
public final class StopBook {

    private final String symbol;
    private final List<StopOrder> buyStops = new ArrayList<>();
    private final List<StopOrder> sellStops = new ArrayList<>();
    private final Map<String, StopOrder> byId = new LinkedHashMap<>();

    public StopBook(String symbol) {
        this.symbol = Objects.requireNonNull(symbol, "symbol");
    }

    public String symbol() {
        return symbol;
    }

    public void add(StopOrder stop) {
        if (byId.putIfAbsent(stop.id(), stop) != null) {
            throw new IllegalArgumentException("duplicate stop id: " + stop.id());
        }
        stopsFor(stop.side()).add(stop);
    }

    public StopOrder cancel(String stopId) {
        StopOrder stop = byId.remove(stopId);
        if (stop != null) {
            stopsFor(stop.side()).remove(stop);
        }
        return stop;
    }

    public StopOrder get(String stopId) {
        return byId.get(stopId);
    }

    public int size() {
        return byId.size();
    }

    /**
     * Trigger stops whose price condition the last traded price now satisfies.
     *
     * @return the triggered orders as live incoming orders, insertion order preserved
     */
    public List<IncomingOrder> onLastTradedPrice(double lastPrice) {
        List<IncomingOrder> triggered = new ArrayList<>();
        trigger(buyStops, lastPrice, OrderSide.BUY, triggered);
        trigger(sellStops, lastPrice, OrderSide.SELL, triggered);
        return List.copyOf(triggered);
    }

    private void trigger(List<StopOrder> stops, double lastPrice, OrderSide side, List<IncomingOrder> out) {
        List<StopOrder> survivors = new ArrayList<>(stops.size());
        for (StopOrder stop : stops) {
            boolean crossed = side == OrderSide.BUY ? lastPrice >= stop.stopPrice() : lastPrice <= stop.stopPrice();
            if (crossed) {
                byId.remove(stop.id());
                out.add(toIncoming(stop));
            } else {
                survivors.add(stop);
            }
        }
        stops.clear();
        stops.addAll(survivors);
    }

    private static IncomingOrder toIncoming(StopOrder stop) {
        boolean limit = stop.type() == OrderType.STOP_LIMIT;
        return new IncomingOrder(
                stop.id(),
                stop.participantId(),
                stop.symbol(),
                stop.side(),
                limit ? OrderType.LIMIT : OrderType.MARKET,
                stop.quantity(),
                limit ? stop.limitPrice() : 0.0);
    }

    private List<StopOrder> stopsFor(OrderSide side) {
        return side == OrderSide.BUY ? buyStops : sellStops;
    }
}
