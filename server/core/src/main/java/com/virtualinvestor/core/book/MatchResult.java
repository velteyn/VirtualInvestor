package com.virtualinvestor.core.book;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of matching one incoming order.
 *
 * @param fills          executions, maker first
 * @param filledQuantity total quantity executed
 * @param rested         true when an unfilled limit remainder was placed in the book
 * @param rejection      a reason when the order could not trade at all
 */
public record MatchResult(
        List<Fill> fills,
        long filledQuantity,
        boolean rested,
        String rejection) {

    public MatchResult {
        fills = List.copyOf(fills);
    }

    public boolean rejected() {
        return rejection != null;
    }

    public Optional<String> rejectionReason() {
        return Optional.ofNullable(rejection);
    }
}
