package com.virtualinvestor.core.session;

import com.virtualinvestor.core.domain.SessionPhase;

/**
 * Durations, in world ticks, of an intraday session's phases. Ticks are offsets from the
 * session start (0-based); anything at or past the total is CLOSED.
 */
public record SessionSchedule(
        long preOpenTicks,
        long openingAuctionTicks,
        long continuousTicks,
        long closingAuctionTicks) {

    public SessionSchedule {
        if (preOpenTicks < 0L) {
            throw new IllegalArgumentException("preOpenTicks must not be negative");
        }
        if (openingAuctionTicks <= 0L || continuousTicks <= 0L || closingAuctionTicks <= 0L) {
            throw new IllegalArgumentException("auction and continuous durations must be positive");
        }
    }

    /** A session of {@code totalTicks} split 5% pre-open, 2% open auction, 5% close auction, rest continuous. */
    public static SessionSchedule standard(long totalTicks) {
        if (totalTicks < 40L) {
            throw new IllegalArgumentException("totalTicks too small for a standard schedule: " + totalTicks);
        }
        long preOpen = Math.max(1L, Math.round(totalTicks * 0.05));
        long opening = Math.max(1L, Math.round(totalTicks * 0.02));
        long closing = Math.max(1L, Math.round(totalTicks * 0.05));
        long continuous = totalTicks - preOpen - opening - closing;
        if (continuous <= 0L) {
            throw new IllegalArgumentException("totalTicks too small for a standard schedule: " + totalTicks);
        }
        return new SessionSchedule(preOpen, opening, continuous, closing);
    }

    public long totalTicks() {
        return preOpenTicks + openingAuctionTicks + continuousTicks + closingAuctionTicks;
    }

    public long openingAuctionStart() {
        return preOpenTicks;
    }

    public long continuousStart() {
        return preOpenTicks + openingAuctionTicks;
    }

    public long closingAuctionStart() {
        return continuousStart() + continuousTicks;
    }

    public SessionPhase phaseAt(long tickOffset) {
        if (tickOffset < preOpenTicks) {
            return SessionPhase.PRE_OPEN;
        }
        if (tickOffset < continuousStart()) {
            return SessionPhase.OPENING_AUCTION;
        }
        if (tickOffset < closingAuctionStart()) {
            return SessionPhase.CONTINUOUS;
        }
        if (tickOffset < totalTicks()) {
            return SessionPhase.CLOSING_AUCTION;
        }
        return SessionPhase.CLOSED;
    }
}
