package com.virtualinvestor.core.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final double EPS = 1e-9;

    @Test
    void averagePriceIsWeightedByQuantity() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 100.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 110.0);

        Position position = account.position("ACME");
        assertEquals(20L, position.netQty());
        assertEquals(105.0, position.avgPrice(), EPS);
        assertEquals(97_900.0, account.cash(), EPS);
    }

    @Test
    void reducingALongRealizesProfit() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 100.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 110.0);
        account.applyFill("ACME", OrderSide.SELL, 5L, 120.0);

        assertEquals(75.0, account.realizedPnl(), EPS);
        assertEquals(15L, account.position("ACME").netQty());
        assertEquals(105.0, account.position("ACME").avgPrice(), EPS);
        assertEquals(98_500.0, account.cash(), EPS);
    }

    @Test
    void partialCloseBooksHalfTheGainPerSymbol() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 100.0);
        account.applyFill("BETA", OrderSide.BUY, 10L, 50.0);
        account.applyFill("ACME", OrderSide.SELL, 5L, 120.0);

        assertEquals(100.0, account.realizedFor("ACME"), EPS);
        assertEquals(100.0, account.realizedPnl(), EPS);
        assertEquals(0.0, account.realizedFor("BETA"), EPS);
        assertEquals(0.0, account.realizedFor("NEVER_TRADED"), EPS);
    }

    @Test
    void flipAttributesTheClosedLegPerSymbol() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 100.0);
        account.applyFill("ACME", OrderSide.SELL, 15L, 110.0);

        assertEquals(100.0, account.realizedFor("ACME"), EPS);
        assertEquals(100.0, account.realizedPnl(), EPS);
        assertEquals(-5L, account.position("ACME").netQty());
    }

    @Test
    void reducingAShortRealizesProfit() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.SELL, 10L, 100.0);
        assertEquals(-10L, account.position("ACME").netQty());
        assertEquals(101_000.0, account.cash(), EPS);

        account.applyFill("ACME", OrderSide.BUY, 4L, 90.0);
        assertEquals(40.0, account.realizedPnl(), EPS);
        assertEquals(-6L, account.position("ACME").netQty());
        assertEquals(100.0, account.position("ACME").avgPrice(), EPS);
        assertEquals(100_640.0, account.cash(), EPS);
    }

    @Test
    void unrealizedAndEquityTrackTheMark() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 100.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 110.0);
        account.applyFill("ACME", OrderSide.SELL, 5L, 120.0);

        Map<String, Double> marks = Map.of("ACME", 115.0);
        assertEquals(150.0, account.unrealizedPnl(marks), EPS);
        assertEquals(100_225.0, account.equity(marks), EPS);
    }

    @Test
    void flippingAPositionRealizesAndReopensAtTheFillPrice() {
        Account account = new Account("h1", 100_000.0);
        account.applyFill("ACME", OrderSide.BUY, 10L, 100.0);
        account.applyFill("ACME", OrderSide.SELL, 15L, 120.0);

        assertEquals(200.0, account.realizedPnl(), EPS);
        Position position = account.position("ACME");
        assertEquals(-5L, position.netQty());
        assertEquals(120.0, position.avgPrice(), EPS);
        assertEquals(100_800.0, account.cash(), EPS);
    }
}
