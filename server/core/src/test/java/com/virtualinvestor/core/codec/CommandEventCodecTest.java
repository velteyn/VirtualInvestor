package com.virtualinvestor.core.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.SessionPhase;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.news.ReliabilityTier;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandEventCodecTest {

    private static final List<Object> SAMPLES = List.of(
            new Command.PlaceOrder("cmd-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5, 0.0, 7L),
            new Command.ModifyOrder("cmd-2", "ord-1", 43.0, 0.0, 8L),
            new Command.CancelOrder("cmd-3", "ord-1", 9L),
            new Command.SetArmed("cmd-4", "part-1", true, 10L),
            new Command.CreateRound("cmd-5", "round-1", 123456789L, 100_000L, 18_000L, "realistic"),
            new Command.PauseRound("cmd-6", "round-1", 11L),
            new Command.ResumeRound("cmd-7", "round-1", 12L),
            new Command.JoinRound("cmd-8", "round-1", "part-1", false),
            new Command.Reconnect("cmd-9", "round-1", "part-1", "token-xyz"),
            new GameEvent.Tick(1L, 100L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5,
                    0L, 100L, OrderStatus.WORKING, OrderEventKind.NEW, null, 1L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5,
                    0L, 100L, OrderStatus.REJECTED, OrderEventKind.REJECTED, "bad price", 2L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5,
                    0L, 100L, OrderStatus.WORKING, OrderEventKind.RESTED, null, 3L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.SELL, OrderType.STOP, 100L, 41.0,
                    0L, 100L, OrderStatus.WORKING, OrderEventKind.TRIGGERED, null, 4L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5,
                    40L, 60L, OrderStatus.PARTIAL, OrderEventKind.PARTIAL_FILL, null, 5L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5,
                    100L, 0L, OrderStatus.FILLED, OrderEventKind.FILL, null, 6L),
            new GameEvent.OrderReport("ord-1", "part-1", "ACME", OrderSide.BUY, OrderType.LIMIT, 100L, 42.5,
                    0L, 100L, OrderStatus.CANCELLED, OrderEventKind.CANCELLED, "user", 7L),
            new GameEvent.PositionUpdated("part-1", "ACME", 40L, 42.5, 0.0, 0.0, 8L),
            new GameEvent.AccountUpdated("part-1", 98_320.0, 0.0, 4.0, 0.0, 8L),
            new GameEvent.RoundCreated("round-1", 123456789L, 100_000L, 18_000L, "realistic", 0L),
            new GameEvent.WarmupComplete("round-1", 100L),
            new GameEvent.AuctionOpened("ACME", SessionPhase.OPENING_AUCTION, 10L),
            new GameEvent.AuctionClosed("ACME", SessionPhase.OPENING_AUCTION, 42.25, 1500L, 20L),
            new GameEvent.SessionOpened("round-1", 21L),
            new GameEvent.SessionClosed("round-1", 18_000L),
            new GameEvent.RoundPaused("round-1", 1000L),
            new GameEvent.RoundResumed("round-1", 1001L),
            new GameEvent.RoundSettled("round-1", 18_001L),
            new GameEvent.NewsReleased("news-1", "THE WIRE", NewsScope.COMPANY, "ACME", "tech",
                    "ACME beats guidance", "Body text", 0.05, 300L, NewsStage.RUMOR, 55L,
                    ReliabilityTier.WIRE),
            new GameEvent.NewsConfirmed("news-1", "ACME confirms", 80L),
            new GameEvent.NewsDenied("news-1", "ACME denies", 80L),
            new GameEvent.MarketFactorMoved(0.01, -0.02, 60L),
            new GameEvent.FundamentalJumped("ACME", 100.0, 105.0, 61L),
            new GameEvent.VolatilityStateChanged("ACME", 0.2, 0.35, 62L),
            new GameEvent.BookChanged("ACME", 99.5, 300L, 100.5, 250L, 100.0, 63L),
            new GameEvent.TradePrinted("ACME", 100.0, 50L, OrderSide.BUY, 64L),
            new GameEvent.BarClosed("ACME", "1m", 1_700_000_000L, 100.0, 101.0, 99.0, 100.5, 5000L, 65L),
            new GameEvent.MarginCallIssued("part-1", 5000.0, 6000.0, 66L),
            new GameEvent.LiquidationExecuted("part-1", "ACME", 100L, 98.0, 67L));

    @Test
    void everyCommandAndEventRoundTrips() {
        for (Object sample : SAMPLES) {
            String encoded = RecordTextCodec.encode(sample);
            Object decoded = RecordTextCodec.decode(encoded, sample.getClass());
            assertEquals(sample, decoded, sample.getClass().getSimpleName());
        }
    }

    @Test
    void stringsWithSeparatorsRoundTrip() {
        GameEvent.NewsReleased sample = new GameEvent.NewsReleased(
                "news-2", "THE WIRE", NewsScope.MACRO, "", "",
                "line one\tline\ntwo\\end", "body with \r carriage", -0.03, 0L, NewsStage.CONFIRMED, 5L,
                ReliabilityTier.PATRON);
        String encoded = RecordTextCodec.encode(sample);
        GameEvent.NewsReleased decoded = RecordTextCodec.decode(encoded, GameEvent.NewsReleased.class);
        assertEquals(sample, decoded);
    }
}
