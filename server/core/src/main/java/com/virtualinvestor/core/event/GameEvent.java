package com.virtualinvestor.core.event;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.SessionPhase;

/**
 * A fact the world recorded (design D13). Replaying events alone reconstructs the world;
 * commands are inputs, events are the durable output. All implementations are immutable
 * records nested here so the set stays closed and exhaustive.
 */
public sealed interface GameEvent {

    long tick();

    record Tick(long tick, long logicalMillis) implements GameEvent {
    }

    /**
     * A self-contained order update, modelled on FIX's ExecutionReport(35=8): every report
     * carries the order's full identity (side, type, quantity, price) and its running fill
     * state (filled, leaves, status), so a consumer can rebuild the working-order book from the
     * stream alone. `event` names why the report was sent.
     */
    record OrderReport(
            String orderId,
            String participantId,
            String symbol,
            OrderSide side,
            OrderType orderType,
            long quantity,
            double price,
            long filledQuantity,
            long leavesQuantity,
            OrderStatus status,
            OrderEventKind event,
            String reason,
            long tick) implements GameEvent {
    }

    record RoundCreated(String roundId, long seed, long startingCapital, long durationTicks, String preset, long tick) implements GameEvent {
    }

    record WarmupComplete(String roundId, long tick) implements GameEvent {
    }

    record AuctionOpened(String symbol, SessionPhase phase, long tick) implements GameEvent {
    }

    record AuctionClosed(String symbol, SessionPhase phase, double clearingPrice, long matchedVolume, long tick) implements GameEvent {
    }

    record SessionOpened(String roundId, long tick) implements GameEvent {
    }

    record SessionClosed(String roundId, long tick) implements GameEvent {
    }

    record RoundPaused(String roundId, long tick) implements GameEvent {
    }

    record RoundResumed(String roundId, long tick) implements GameEvent {
    }

    record RoundSettled(String roundId, long tick) implements GameEvent {
    }

    record NewsReleased(
            String newsId,
            String source,
            NewsScope scope,
            String symbol,
            String sector,
            String headline,
            String body,
            double magnitude,
            long durationTicks,
            NewsStage stage,
            long tick,
            com.virtualinvestor.core.news.ReliabilityTier tier) implements GameEvent {
    }

    record NewsConfirmed(String newsId, String headline, long tick) implements GameEvent {
    }

    record NewsDenied(String newsId, String headline, long tick) implements GameEvent {
    }

    record MarketFactorMoved(double previous, double current, long tick) implements GameEvent {
    }

    record FundamentalJumped(String symbol, double previous, double current, long tick) implements GameEvent {
    }

    record VolatilityStateChanged(String scope, double previous, double current, long tick) implements GameEvent {
    }

    record BookChanged(
            String symbol,
            double bestBid,
            long bestBidSize,
            double bestAsk,
            long bestAskSize,
            double lastPrice,
            long tick) implements GameEvent {
    }

    record TradePrinted(String symbol, double price, long quantity, OrderSide aggressorSide, long tick) implements GameEvent {
    }

    record BarClosed(
            String symbol,
            String interval,
            long barTime,
            double open,
            double high,
            double low,
            double close,
            long volume,
            long tick) implements GameEvent {
    }

    /**
     * A participant's position after a fill, so mirrors track holdings live instead of
     * waiting for a resync. Emitted per counterparty with the account below.
     */
    record PositionUpdated(String participantId, String symbol, long netQty, double avgPrice, double realized,
            double locked, long tick) implements GameEvent {
    }

    /** A participant's cash state after a fill (cash moved and/or fees debited). */
    record AccountUpdated(String participantId, double cash, double realizedPnl, double feesPaid,
            double lockedMargin, long tick) implements GameEvent {
    }

    record MarginCallIssued(String participantId, double equity, double requirement, long tick) implements GameEvent {
    }

    record LiquidationExecuted(String participantId, String symbol, long quantity, double price, long tick) implements GameEvent {
    }

    /** A futures position cash-settled at expiry (design D6): replayable settlement economics. */
    record FuturesSettled(String participantId, String symbol, long quantity, double settlementPrice,
            long tick) implements GameEvent {
    }

    /** An option position cash-settled at the bell — exercised when in the money, lapsed otherwise. */
    record OptionSettled(String participantId, String symbol, long quantity, double settlementPrice,
            long tick) implements GameEvent {
    }
}
