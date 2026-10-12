package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.book.OrderBook;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.instrument.SeriesLister;
import com.virtualinvestor.core.instrument.SeriesSymbols;
import com.virtualinvestor.core.pricing.OptionPricing;
import com.virtualinvestor.core.rules.RealismPreset;
import org.junit.jupiter.api.Test;

/** Group 6.1: makers quote futures and near-money options around derived references. */
class MarketMakerDerivativesTest {

    private static final long MINUTE_MILLIS = 60_000L;

    @Test
    void futuresBookQuotedNearSpot() {
        RunningRound round = continuousRound(61L);
        for (int i = 0; i < 60; i++) {
            round.step();
        }
        OrderBook book = round.engines().get("NVDO-F").book();
        Double ask = book.bestAskPrice();
        Double bid = book.bestBidPrice();
        assertNotNull(ask, "makers should quote the future");
        assertNotNull(bid, "makers should quote the future");

        double spot = round.engines().get("NVDO").lastPrice();
        double mid = (ask + bid) / 2.0;
        assertTrue(Math.abs(mid - spot) / spot < 0.05,
                "future mid " + mid + " should track spot " + spot);
    }

    @Test
    void nearMoneyCallQuotedNearFormula() {
        RunningRound round = continuousRound(62L);
        for (int i = 0; i < 60; i++) {
            round.step();
        }
        Instrument spot = InstrumentUniverse.defaultUniverse().find("NVDO").orElseThrow();
        double atm = SeriesLister.atmStrike(spot.referencePrice(), spot.tickSize());
        String call = SeriesSymbols.callSymbol("NVDO", atm);

        OrderBook book = round.engines().get(call).book();
        Double ask = book.bestAskPrice();
        assertNotNull(ask, "makers should quote the near-money call " + call);

        double underlying = round.engines().get("NVDO").lastPrice();
        double formula = OptionPricing.call(underlying, atm, 0.001, spot.baseVolatility());
        assertTrue(Math.abs(ask - formula) / Math.max(formula, 0.01) < 0.5,
                "call ask " + ask + " should sit near formula " + formula);
    }

    @Test
    void takersNeverTouchSeriesBooks() {
        RunningRound round = continuousRound(63L);
        java.util.Set<String> takers = new java.util.HashSet<>(java.util.List.of(
                "TIDE", "LEDGER", "BLITZ", "VULTURE", "human"));
        java.util.Set<String> violators = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            for (GameEvent event : round.step()) {
                if (event instanceof GameEvent.OrderReport report
                        && takers.contains(report.participantId())
                        && SeriesSymbols.isSeriesSymbol(report.symbol())) {
                    violators.add(report.participantId() + ":" + report.symbol());
                }
            }
        }
        assertTrue(violators.isEmpty(), "takers on series books: " + violators);
    }

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-maker", seed,
                new LaunchConfig(100_000.0, 180L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        return round;
    }
}
