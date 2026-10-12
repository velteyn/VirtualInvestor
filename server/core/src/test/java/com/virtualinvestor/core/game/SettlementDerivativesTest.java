package com.virtualinvestor.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.HumanParticipant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementDerivativesTest {

    @Test
    void mixedBookSettlesWithNothingSurvivingTheBell() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        trader.account().applyFill("ACME", OrderSide.BUY, 100L, 100.0);
        trader.account().applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);
        trader.account().applyOptionFill("NVDO-C640", OrderSide.BUY, 5L, 12.0);
        trader.account().applyOptionFill("NVDO-P640", OrderSide.SELL, 5L, 12.0);

        Map<String, Double> closing = Map.of("ACME", 110.0, "NVDO", 660.0);
        Map<String, Double> capitals = new LinkedHashMap<>(Map.of("h1", 100_000.0));
        Settlement.Result result = Settlement.settle(List.of(trader), closing, capitals, 999L, "round-9");

        assertTrue(trader.account().positions().isEmpty(), "nothing survives the bell");
        assertTrue(result.events().stream().anyMatch(e -> e instanceof GameEvent.FuturesSettled settled
                && settled.symbol().equals("NVDO-F")
                && settled.quantity() == 10L
                && settled.settlementPrice() == 660.0));
        assertTrue(result.events().stream().anyMatch(e -> e instanceof GameEvent.OptionSettled settled
                && settled.symbol().equals("NVDO-C640")
                && settled.settlementPrice() == 20.0));
        assertTrue(result.events().stream().anyMatch(e -> e instanceof GameEvent.OptionSettled settled
                && settled.symbol().equals("NVDO-P640")
                && settled.settlementPrice() == 0.0));
        assertTrue(result.events().stream().anyMatch(GameEvent.RoundSettled.class::isInstance));

        // ACME +1000; futures (660-640)*10 = +200; long call (20-12)*5 = +40;
        // short put keeps 60 premium, lapses at zero.
        assertEquals(1000.0 + 200.0 + 40.0 + 60.0, trader.account().realizedPnl(), 1e-6);
        assertEquals(101_300.0, trader.account().equity(closing), 1e-6);
        assertEquals(1, result.standings().size());
    }
}
