package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.SessionPhase;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Periodic full-market realism watch (tolerance backstop is a safety net, not a gameplay
 * path): runs a complete 180-minute session on the economics-baseline seed, fires one large
 * player sweep mid-session, and verifies the market behaves the way the liquidity spec says —
 * fade and thinning under stress, requote on calm, gate never tripped. Deterministic: same
 * seed, same report. Re-run any time with {@code mvn -pl runtime -am test
 * -Dtest=MarketRealismTest} and read the printed watch report in the surefire output.
 */
class MarketRealismTest {

    private static final long MINUTE_MILLIS = 60_000L;
    private static final long SEED = 42L;
    private static final String SWEEP_SYMBOL = "PEAR";
    private static final long SWEEP_TICK = 90L;
    /** Lowest maker tolerance (CROWN): the gate must never trip in a live session. */
    private static final double GATE_FLOOR = 2.25;

    @Test
    void fullSessionBehavesRealistically() {
        RunningRound round = new RunningRound("round-watch", SEED,
                new LaunchConfig(100_000.0, 180L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        // Fee-aware economics like the calibration baselines: tariffs round-robined over
        // the seed schedules by roster order, so the printed standings are net of costs.
        FeeSchedule[] tariffs = {
                FeeSchedule.fixed(0.005, 1.00),
                FeeSchedule.fixed(0.01, 2.00),
                FeeSchedule.fixed(0.02, 5.00)};
        List<? extends com.virtualinvestor.core.participant.Participant> roster = round.participants();
        for (int i = 0; i < roster.size(); i++) {
            round.setFeeSchedule(roster.get(i).id(), tariffs[i % tariffs.length]);
        }
        java.util.Set<String> makerIds = new java.util.HashSet<>();
        for (var participant : round.participants()) {
            if (participant.displayName().equals("MARK")
                    || participant.displayName().equals("ATLAS BANK")
                    || participant.displayName().equals("CROWN FUND")) {
                makerIds.add(participant.id());
            }
        }

        long trades = 0;
        long rejectedNoLiquidity = 0;
        long sweepFills = 0;
        double maxToxicity = 0.0;
        String hottestSymbol = "-";
        long gateTrips = 0;
        long emptySideTicks = 0;
        Map<String, Long> emptySideBySymbol = new HashMap<>();
        // Ask-side resting depth on the sweep symbol around the sweep, for the fade story.
        long depthBeforeSweep = -1;
        long depthAfterSweep = -1;
        long depthAtEnd = -1;
        Map<String, Double> symbolMaxToxicity = new HashMap<>();
        Map<String, Double> symbolToxSum = new HashMap<>();
        long toxSamples = 0;
        // Touch vs full-ladder depth around each requote wave (touch cadence is 50
        // ticks; outer rungs refresh on quadratic multiples) plus toxicity, to see
        // what actually rebuilds after the sweep.
        Map<Long, String> waveSamples = new TreeMap<>();
        long maxLadderAfterSweep = 0;
        long multiLevelTicks = 0;
        Map<String, Long> makerQuoteTicks = new HashMap<>();
        long calmTicks = 0;

        for (long tick = 0; tick < 180; tick++) {
            if (tick == SWEEP_TICK) {
                depthBeforeSweep = askDepth(round, SWEEP_SYMBOL);
                fireSweep(round, tick);
            }
            List<GameEvent> events = round.step();
            for (GameEvent event : events) {
                if (event instanceof GameEvent.TradePrinted print) {
                    trades++;
                    if (print.symbol().equals(SWEEP_SYMBOL) && print.tick() >= SWEEP_TICK
                            && print.aggressorSide() == OrderSide.BUY) {
                        sweepFills += print.quantity();
                    }
                }
                if (event instanceof GameEvent.OrderReport report
                        && report.event() == OrderEventKind.REJECTED
                        && report.reason() != null
                        && report.reason().contains("no liquidity")) {
                    rejectedNoLiquidity++;
                }
            }
            if (tick == SWEEP_TICK + 1) {
                depthAfterSweep = askDepth(round, SWEEP_SYMBOL);
            }
            boolean continuous = round.session().phase() == SessionPhase.CONTINUOUS;
            for (String symbol : round.engines().keySet()) {
                double tox = round.toxicity(symbol);
                symbolMaxToxicity.merge(symbol, tox, Math::max);
                if (continuous) {
                    symbolToxSum.merge(symbol, tox, Double::sum);
                }
                if (tox > maxToxicity) {
                    maxToxicity = tox;
                    hottestSymbol = symbol;
                }
                if (continuous && tox >= GATE_FLOOR) {
                    gateTrips++;
                }
            }
            if (continuous && sideEmpty(round, SWEEP_SYMBOL)) {
                emptySideTicks++;
            }
            if (continuous) {
                for (String symbol : round.engines().keySet()) {
                    if (sideEmpty(round, symbol)) {
                        emptySideBySymbol.merge(symbol, 1L, Long::sum);
                    }
                }
            }
            if (continuous) {
                toxSamples++;
                calmTicks++;
                for (String symbol : round.engines().keySet()) {
                    long makers = round.engines().get(symbol).book().orders().stream()
                            .map(com.virtualinvestor.core.book.RestingOrder::participantId)
                            .filter(makerIds::contains)
                            .distinct()
                            .count();
                    makerQuoteTicks.merge(symbol + ":" + makers, 1L, Long::sum);
                }
            }
            if (tick >= SWEEP_TICK - 1 && (tick % 10 == 0 || tick == SWEEP_TICK - 1
                    || tick == SWEEP_TICK + 1 || tick == 179)) {
                var book = round.engines().get(SWEEP_SYMBOL).book();
                waveSamples.put(tick, "tox=" + String.format("%.2f", round.toxicity(SWEEP_SYMBOL))
                        + " touch=" + book.aggregateQuantity(OrderSide.SELL, 1)
                        + " ladder5=" + book.aggregateQuantity(OrderSide.SELL, 5));
            }
            if (tick > SWEEP_TICK) {
                var book = round.engines().get(SWEEP_SYMBOL).book();
                maxLadderAfterSweep = Math.max(maxLadderAfterSweep,
                        book.aggregateQuantity(OrderSide.SELL, 5));
                if (book.aggregateQuantity(OrderSide.SELL, 5) > book.aggregateQuantity(OrderSide.SELL, 1)) {
                    multiLevelTicks++;
                }
            }
        }
        depthAtEnd = askDepth(round, SWEEP_SYMBOL);

        List<Map.Entry<String, Double>> hottest = new ArrayList<>(symbolMaxToxicity.entrySet());
        hottest.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<String, Double> topToxicity = new TreeMap<>();
        hottest.stream().limit(5).forEach(e -> topToxicity.put(e.getKey(), e.getValue()));

        System.out.println("MARKET WATCH seed=" + SEED + " ticks=180 sweep=" + SWEEP_SYMBOL
                + "@" + SWEEP_TICK + " sweepFills=" + sweepFills);
        System.out.println("  trades=" + trades + " rejectedNoLiquidity=" + rejectedNoLiquidity);
        System.out.println("  maxToxicity=" + String.format("%.3f", maxToxicity) + " on "
                + hottestSymbol + " (gate floor " + GATE_FLOOR + ", trips=" + gateTrips + ")");
        System.out.println("  top toxicity by symbol: " + topToxicity);
        Map<String, Double> meanToxicity = new TreeMap<>();
        final long samples = toxSamples;
        symbolToxSum.forEach((symbol, sum) ->
                meanToxicity.put(symbol, sum / Math.max(1, samples)));
        System.out.println("  mean toxicity by symbol: " + meanToxicity);
        System.out.println("  " + SWEEP_SYMBOL + " ask depth(5): before sweep=" + depthBeforeSweep
                + " after=" + depthAfterSweep + " at end=" + depthAtEnd);
        System.out.println("  ticks with an empty " + SWEEP_SYMBOL
                + " side (continuous): " + emptySideTicks);
        System.out.println("  " + SWEEP_SYMBOL + " ask waves (touch vs 5-deep): " + waveSamples);
        System.out.println("  post-sweep: max ladder5=" + maxLadderAfterSweep
                + " ticks with multi-level ask=" + multiLevelTicks);
        Map<String, Double> calmTwoMakers = new TreeMap<>();
        for (String symbol : round.engines().keySet()) {
            long withTwo = 0;
            for (int makers = 2; makers <= 3; makers++) {
                withTwo += makerQuoteTicks.getOrDefault(symbol + ":" + makers, 0L);
            }
            calmTwoMakers.put(symbol, calmTicks == 0 ? 0.0 : (double) withTwo / calmTicks);
        }
        System.out.println("  calm-tick fraction with ≥2 makers quoting: " + calmTwoMakers);
        long calmTotal = Math.max(1, calmTicks * round.engines().size());
        long emptyTotal = emptySideBySymbol.values().stream().mapToLong(Long::longValue).sum();
        double emptyFraction = (double) emptyTotal / calmTotal;
        System.out.println("  empty-side fraction across all symbols: "
                + String.format("%.3f", emptyFraction));
        Map<String, Double> marks = round.marks();
        record Standing(String name, double equity, double fees) {
        }
        List<Standing> table = new ArrayList<>();
        for (var participant : round.participants()) {
            var account = participant.account();
            table.add(new Standing(participant.displayName(), account.equity(marks),
                    account.feesPaid()));
        }
        table.sort((a, b) -> Double.compare(b.equity(), a.equity()));
        List<String> standings = new ArrayList<>();
        for (Standing standing : table) {
            standings.add(String.format("%s %.2f (fees %.0f)", standing.name(),
                    standing.equity(), standing.fees()));
        }
        System.out.println("  standings: " + standings);

        assertTrue(sweepFills > 0, "the mid-session sweep should execute");
        assertTrue(trades > 2_000, "bots should trade all session long, got " + trades);
        assertTrue(rejectedNoLiquidity <= trades / 200,
                "rejects for no liquidity stay under 0.5% of trades, got " + rejectedNoLiquidity
                        + "/" + trades);
        assertTrue(emptyFraction <= 0.30,
                "books sit empty at most 30% of symbol-ticks, got " + emptyFraction);
        long wellQuoted = calmTwoMakers.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("VNDX") && entry.getValue() >= 0.5)
                .count();
        assertTrue(wellQuoted >= 12,
                "≥2 makers quote most of the time on most equities, got " + wellQuoted + "/20");
        assertTrue(maxToxicity > 0.1,
                "the sweep should visibly move the toxicity sensor, got " + maxToxicity);
        assertTrue(gateTrips == 0,
                "the tolerance gate is a safety net: it must never trip in a live session");
    }

    private static void fireSweep(RunningRound round, long tick) {
        Double ask = round.engines().get(SWEEP_SYMBOL).book().bestAskPrice();
        double cash = round.participant("human").account().cash();
        long qty = ask == null ? 100L : Math.max(1L, (long) (cash * 0.8 / ask));
        round.enqueue(new Command.PlaceOrder("watch-sweep", "human", SWEEP_SYMBOL, OrderSide.BUY,
                OrderType.MARKET, qty, 0.0, 0.0, tick));
    }

    private static long askDepth(RunningRound round, String symbol) {
        return round.engines().get(symbol).book().aggregateQuantity(OrderSide.SELL, 5);
    }

    private static boolean sideEmpty(RunningRound round, String symbol) {
        var book = round.engines().get(symbol).book();
        return book.bestBidPrice() == null || book.bestAskPrice() == null;
    }
}
