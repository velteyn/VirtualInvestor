package com.virtualinvestor.runtime;

/**
 * A bank's broker tariff applied per execution (player-accounts 5.1): the greater
 * of a ticket minimum and a per-share commission on the filled quantity. Derivative
 * fills pay a percentage of notional under the bank's series terms instead. A pure
 * function of the fill, so replays deduct identically.
 */
public interface FeeSchedule {

    double feeFor(long filledQuantity);

    /**
     * Percentage-of-notional fee for a derivatives fill. The default is the legacy
     * fallback (equity terms applied to contracts) for schedules that predate series
     * terms; bank schedules built with series terms override it.
     */
    default double feeForSeries(long filledContracts, double notional) {
        return feeFor(filledContracts);
    }

    /** No fees (tests and any participant without a bank). */
    static FeeSchedule none() {
        return new FeeSchedule() {
            @Override
            public double feeFor(long filledQuantity) {
                return 0.0;
            }

            @Override
            public double feeForSeries(long filledContracts, double notional) {
                return 0.0;
            }
        };
    }

    static FeeSchedule fixed(double feePerShare, double ticketMinimum) {
        if (feePerShare < 0.0 || ticketMinimum < 0.0) {
            throw new IllegalArgumentException("fee terms must not be negative");
        }
        return new FeeSchedule() {
            @Override
            public double feeFor(long filledQuantity) {
                return Math.max(ticketMinimum, feePerShare * filledQuantity);
            }
        };
    }

    static FeeSchedule fixed(double feePerShare, double ticketMinimum, double seriesRate,
            double seriesMinimum) {
        if (seriesRate < 0.0 || seriesMinimum < 0.0) {
            throw new IllegalArgumentException("series fee terms must not be negative");
        }
        FeeSchedule equity = fixed(feePerShare, ticketMinimum);
        return new FeeSchedule() {
            @Override
            public double feeFor(long filledQuantity) {
                return equity.feeFor(filledQuantity);
            }

            @Override
            public double feeForSeries(long filledContracts, double notional) {
                if (!Double.isFinite(notional) || notional < 0.0) {
                    throw new IllegalArgumentException("notional must be finite and non-negative");
                }
                return Math.max(seriesMinimum, seriesRate * notional);
            }
        };
    }
}
