package com.virtualinvestor.core.auction;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Volume-maximizing call auction used for the open and the close. The clearing price is the
 * candidate price that matches the most volume; ties break toward the smaller demand/supply
 * imbalance, then toward the reference price. The smaller side fills fully and the larger side
 * is allocated pro-rata with deterministic largest-remainder rounding.
 */
public final class Auction {

    private final List<AuctionOrder> orders = new ArrayList<>();

    public void submit(AuctionOrder order) {
        orders.add(order);
    }

    public int size() {
        return orders.size();
    }

    public List<AuctionOrder> orders() {
        return List.copyOf(orders);
    }

    public AuctionResult clear(double referencePrice) {
        if (orders.isEmpty()) {
            return new AuctionResult(referencePrice, 0L, List.of());
        }

        SortedSet<Double> candidates = new TreeSet<>();
        for (AuctionOrder order : orders) {
            if (!order.market()) {
                candidates.add(order.limitPrice());
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(referencePrice);
        }

        double bestPrice = referencePrice;
        long bestMatched = -1L;
        long bestImbalance = Long.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (double price : candidates) {
            long demand = 0L;
            long supply = 0L;
            for (AuctionOrder order : orders) {
                if (order.willingAt(price)) {
                    if (order.side() == OrderSide.BUY) {
                        demand += order.quantity();
                    } else {
                        supply += order.quantity();
                    }
                }
            }
            long matched = Math.min(demand, supply);
            long imbalance = Math.abs(demand - supply);
            double distance = Math.abs(price - referencePrice);
            boolean better = matched > bestMatched
                    || (matched == bestMatched && imbalance < bestImbalance)
                    || (matched == bestMatched && imbalance == bestImbalance && distance < bestDistance - 1e-12);
            if (better) {
                bestMatched = matched;
                bestImbalance = imbalance;
                bestPrice = price;
                bestDistance = distance;
            }
        }

        if (bestMatched <= 0L) {
            return new AuctionResult(referencePrice, 0L, List.of());
        }

        List<AuctionOrder> buyers = new ArrayList<>();
        List<AuctionOrder> sellers = new ArrayList<>();
        for (AuctionOrder order : orders) {
            if (order.willingAt(bestPrice)) {
                (order.side() == OrderSide.BUY ? buyers : sellers).add(order);
            }
        }
        long demand = total(buyers);
        long supply = total(sellers);
        long matched = Math.min(demand, supply);

        List<AuctionFill> fills = new ArrayList<>();
        if (demand <= supply) {
            for (AuctionOrder order : buyers) {
                fills.add(fill(order, order.quantity(), bestPrice));
            }
            appendAllocations(fills, sellers, supply, matched, bestPrice);
        } else {
            for (AuctionOrder order : sellers) {
                fills.add(fill(order, order.quantity(), bestPrice));
            }
            appendAllocations(fills, buyers, demand, matched, bestPrice);
        }
        return new AuctionResult(bestPrice, matched, fills);
    }

    private static void appendAllocations(
            List<AuctionFill> fills, List<AuctionOrder> eligible, long total, long matched, double price) {
        long[] allocations = allocate(eligible, total, matched);
        for (int i = 0; i < eligible.size(); i++) {
            if (allocations[i] > 0L) {
                fills.add(fill(eligible.get(i), allocations[i], price));
            }
        }
    }

    private static long[] allocate(List<AuctionOrder> eligible, long total, long matched) {
        int n = eligible.size();
        long[] out = new long[n];
        if (total <= 0L || matched <= 0L || n == 0) {
            return out;
        }
        double[] fraction = new double[n];
        long allocated = 0L;
        for (int i = 0; i < n; i++) {
            double exact = (double) eligible.get(i).quantity() * matched / total;
            long base = (long) Math.floor(exact);
            out[i] = base;
            allocated += base;
            fraction[i] = exact - base;
        }
        long remaining = matched - allocated;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int byFraction = Double.compare(fraction[b], fraction[a]);
            return byFraction != 0 ? byFraction : Integer.compare(a, b);
        });
        for (int k = 0; k < remaining; k++) {
            out[order[k]]++;
        }
        return out;
    }

    private static long total(List<AuctionOrder> orders) {
        long sum = 0L;
        for (AuctionOrder order : orders) {
            sum += order.quantity();
        }
        return sum;
    }

    private static AuctionFill fill(AuctionOrder order, long quantity, double price) {
        return new AuctionFill(order.id(), order.participantId(), order.side(), quantity, price);
    }
}
