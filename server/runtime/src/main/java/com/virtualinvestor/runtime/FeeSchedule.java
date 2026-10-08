package com.virtualinvestor.runtime;

/**
 * A bank's broker tariff applied per execution (player-accounts 5.1): the greater
 * of a ticket minimum and a per-share commission on the filled quantity. A pure
 * function of the fill, so replays deduct identically.
 */
public interface FeeSchedule {

    double feeFor(long filledQuantity);

    /** No fees (tests and any participant without a bank). */
    static FeeSchedule none() {
        return quantity -> 0.0;
    }

    static FeeSchedule fixed(double feePerShare, double ticketMinimum) {
        if (feePerShare < 0.0 || ticketMinimum < 0.0) {
            throw new IllegalArgumentException("fee terms must not be negative");
        }
        return quantity -> Math.max(ticketMinimum, feePerShare * quantity);
    }
}
