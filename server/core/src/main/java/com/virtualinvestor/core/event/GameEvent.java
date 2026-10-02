package com.virtualinvestor.core.event;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.domain.OrderSide;
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

    record OrderAccepted(String orderId, String participantId, String symbol, long tick) implements GameEvent {
    }

    record OrderRejected(String orderId, String participantId, String symbol, String reason, long tick) implements GameEvent {
    }

    record OrderRested(String orderId, String symbol, double price, long quantity, long tick) implements GameEvent {
    }

    record OrderTriggered(String orderId, String symbol, double price, long tick) implements GameEvent {
    }

    record OrderPartiallyFilled(String orderId, String symbol, double price, long filledQuantity, long tick) implements GameEvent {
    }

    record OrderFilled(String orderId, String symbol, double price, long filledQuantity, long tick) implements GameEvent {
    }

    record OrderCancelled(String orderId, String symbol, String reason, long tick) implements GameEvent {
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
            long tick) implements GameEvent {
    }

    record NewsConfirmed(String newsId, long tick) implements GameEvent {
    }

    record NewsDenied(String newsId, long tick) implements GameEvent {
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

    record MarginCallIssued(String participantId, double equity, double requirement, long tick) implements GameEvent {
    }

    record LiquidationExecuted(String participantId, String symbol, long quantity, double price, long tick) implements GameEvent {
    }
}
