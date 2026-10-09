package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.MacroReactionStrategy;
import com.virtualinvestor.core.bot.MomentumStrategy;
import com.virtualinvestor.core.bot.NewsHunterStrategy;
import com.virtualinvestor.core.bot.NewsSignal;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.ParticipantType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * News-reading-bots 1.1/1.2/1.4: releases reach strategies through the runner,
 * macro headlines never flood every engine, and perception is deterministic.
 */
class NewsSignalDeliveryTest {

    private static BotRunner hunter() {
        return new BotRunner(
                new BotParticipant("rival-5", "VULTURE", ParticipantType.RIVAL_BOT, 1_000_000.0),
                new NewsHunterStrategy(0.01, 40L), null, 0L, 0.01, 0L);
    }

    private static BotRunner macroDesk() {
        return new BotRunner(
                new BotParticipant("inst-2", "MERIDIAN CAPITAL", ParticipantType.INSTITUTION, 1_000_000.0),
                new MacroReactionStrategy(0.01, 1_000L), null, 0L, 0.01, 0L);
    }

    private static InstrumentEngine book() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.submitWithFills(
                new IncomingOrder("bid-1", "m", "ACME", OrderSide.BUY, OrderType.LIMIT, 500L, 99.0), 0L);
        engine.submitWithFills(
                new IncomingOrder("ask-1", "m", "ACME", OrderSide.SELL, OrderType.LIMIT, 500L, 101.0), 0L);
        return engine;
    }

    private static List<GameEvent> act(BotRunner runner, InstrumentEngine engine, long tick,
            NewsSignal signal) {
        return runner.act(engine, 100.0, 0.0, 0.0, 0L, tick, 1_000_000.0, 0.0, signal, fill -> {
        });
    }

    private static long printedQty(List<GameEvent> events) {
        return events.stream()
                .filter(GameEvent.TradePrinted.class::isInstance)
                .mapToLong(event -> ((GameEvent.TradePrinted) event).quantity())
                .sum();
    }

    private static NewsSignal company(double magnitude) {
        return new NewsSignal(magnitude, "ACME", "TECH", NewsScope.COMPANY);
    }

    @Test
    void hunterTakesOnRelease() {
        BotRunner runner = hunter();
        InstrumentEngine engine = book();

        List<GameEvent> events = act(runner, engine, 10L, company(0.03));

        assertEquals(40L, printedQty(events), "the hunter lifts 40 into the headline");
    }

    @Test
    void silenceAndSmallHeadlinesPass() {
        assertTrue(act(hunter(), book(), 10L, null).isEmpty(), "no signal, no trade");
        assertTrue(act(hunter(), book(), 10L, company(0.005)).isEmpty(),
                "below the 0.01 threshold the hunter passes");
    }

    @Test
    void institutionWaitsForTheTape() {
        BotRunner runner = macroDesk();
        InstrumentEngine engine = book();
        NewsSignal macro = new NewsSignal(0.03, "ACME", "TECH", NewsScope.MACRO);

        for (long tick = 10L; tick < 10L + BotRunner.MACRO_NEWS_DELAY_TICKS; tick++) {
            assertTrue(act(runner, engine, tick, tick == 10L ? macro : null).isEmpty(),
                    "the desk holds fire until its delay elapses");
        }
        List<GameEvent> delayed = act(runner, engine, 10L + BotRunner.MACRO_NEWS_DELAY_TICKS, null);
        assertTrue(printedQty(delayed) > 0, "then it takes size");
    }

    @Test
    void delaysDifferByArchetype() {
        assertEquals(0, BotRunner.newsDelayFor(new NewsHunterStrategy(0.01, 40L)));
        assertEquals(0, BotRunner.newsDelayFor(new MomentumStrategy(0.0005, 30L)));
        assertEquals(BotRunner.MACRO_NEWS_DELAY_TICKS,
                BotRunner.newsDelayFor(new MacroReactionStrategy(0.01, 1_000L)));
    }

    @Test
    void jitterIsDeterministicBoundedAndReaderSpecific() {
        NewsSignal stated = company(0.02);
        NewsSignal first = BotRunner.jitter(stated, 42L, "rival-5", 10L);
        NewsSignal repeat = BotRunner.jitter(stated, 42L, "rival-5", 10L);
        assertEquals(first.signedMagnitude(), repeat.signedMagnitude(), 1e-12,
                "same seed, headline and reader read identically");

        double ratio = first.signedMagnitude() / stated.signedMagnitude();
        assertTrue(Math.abs(ratio - 1.0) <= 0.25 + 1e-12, "noise stays within a quarter, got " + ratio);
        assertEquals("ACME", first.symbol());
        assertEquals(NewsScope.COMPANY, first.scope());

        long distinct = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> BotRunner.jitter(stated, 42L, "bot-" + i, 10L).signedMagnitude())
                .distinct()
                .count();
        assertTrue(distinct > 1, "different readers read differently");
    }

    @Test
    void macroReleaseReachesOnlyTheIndex() {
        GameEvent.NewsReleased macro = new GameEvent.NewsReleased("n1", "WIRE", NewsScope.MACRO,
                null, null, "Rates shock", "body", 0.03, 10L, NewsStage.RUMOR, 5L);
        GameEvent.NewsReleased company = new GameEvent.NewsReleased("n2", "WIRE", NewsScope.COMPANY,
                "ACME", "TECH", "Beats", "body", 0.02, 10L, NewsStage.RUMOR, 5L);
        GameEvent.NewsReleased sector = new GameEvent.NewsReleased("n3", "WIRE", NewsScope.SECTOR,
                null, "TECH", "Chip boom", "body", 0.02, 10L, NewsStage.RUMOR, 5L);
        Map<String, String> sectors = Map.of("ACME", "TECH", "BETA", "TECH", "GAMMA", "BANKS");

        Map<String, com.virtualinvestor.core.bot.NewsSignal> routed =
                RunningRound.routeReleases(List.of(macro, company, sector), sectors, "VNDX");

        assertEquals(java.util.Set.of("VNDX", "ACME", "BETA"), routed.keySet(),
                "macro hits the index only, company its symbol, sector its members");
        assertEquals(0.03, routed.get("VNDX").signedMagnitude(), 1e-9);
    }
}
