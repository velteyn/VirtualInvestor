package com.virtualinvestor.core.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountDerivativesTest {

    private static final double EPS = 1e-9;

    @Test
    void futuresOpenLocksMarginWithoutMovingCash() {
        Account account = new Account("h1", 100_000.0);
        account.applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);

        assertEquals(100_000.0, account.cash(), EPS);
        assertEquals(12_800.0, account.lockedMargin(), EPS);
        assertEquals(12_800.0, account.lockedFor("NVDO-F"), EPS);
        assertEquals(87_200.0, account.buyingPower(), EPS);
        assertEquals(10L, account.position("NVDO-F").netQty());
        assertEquals(640.0, account.position("NVDO-F").avgPrice(), EPS);
    }

    @Test
    void futuresEquityCountsOnlyVariation() {
        Account account = new Account("h1", 100_000.0);
        account.applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);

        // Notional would be 6500; equity must reflect only the +100 variation.
        assertEquals(100_100.0, account.equity(Map.of("NVDO-F", 650.0)), EPS);
        assertEquals(99_900.0, account.equity(Map.of("NVDO-F", 630.0)), EPS);
    }

    @Test
    void futuresCloseRealizesAndReleases() {
        Account account = new Account("h1", 100_000.0);
        account.applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);
        account.applyFuturesFill("NVDO-F", OrderSide.SELL, 4L, 650.0, 1280.0);

        assertEquals(40.0, account.realizedPnl(), EPS);
        assertEquals(40.0, account.realizedFor("NVDO-F"), EPS);
        assertEquals(6L, account.position("NVDO-F").netQty());
        assertEquals(12_800.0 * 0.6, account.lockedMargin(), 1e-6);
        assertEquals(100_040.0, account.cash(), EPS);
    }

    @Test
    void futuresFlatReleasesEverything() {
        Account account = new Account("h1", 100_000.0);
        account.applyFuturesFill("NVDO-F", OrderSide.BUY, 10L, 640.0, 1280.0);
        account.applyFuturesFill("NVDO-F", OrderSide.SELL, 10L, 630.0, 1280.0);

        assertEquals(-100.0, account.realizedPnl(), EPS);
        assertTrue(account.position("NVDO-F").isFlat());
        assertEquals(0.0, account.lockedMargin(), EPS);
        assertEquals(99_900.0, account.buyingPower(), EPS);
    }

    @Test
    void optionFillMovesPremiumLikeEquity() {
        Account account = new Account("h1", 100_000.0);
        account.applyOptionFill("NVDO-C640", OrderSide.BUY, 5L, 12.0);

        assertEquals(100_000.0 - 60.0, account.cash(), EPS);
        assertEquals(5L, account.position("NVDO-C640").netQty());
        assertEquals(12.0, account.position("NVDO-C640").avgPrice(), EPS);
        assertEquals(0.0, account.lockedMargin(), EPS);
    }

    @Test
    void optionCloseBooksPremiumDifference() {
        Account account = new Account("h1", 100_000.0);
        account.applyOptionFill("NVDO-C640", OrderSide.BUY, 5L, 12.0);
        account.applyOptionFill("NVDO-C640", OrderSide.SELL, 5L, 20.0);

        assertEquals(40.0, account.realizedPnl(), EPS);
        assertEquals(40.0, account.realizedFor("NVDO-C640"), EPS);
        assertTrue(account.position("NVDO-C640").isFlat());
    }

    @Test
    void invalidFuturesFillsRejected() {
        Account account = new Account("h1", 100_000.0);
        assertThrows(IllegalArgumentException.class,
                () -> account.applyFuturesFill("NVDO-F", OrderSide.BUY, 0L, 640.0, 1280.0));
        assertThrows(IllegalArgumentException.class,
                () -> account.applyFuturesFill("NVDO-F", OrderSide.BUY, 1L, 640.0, -1.0));
    }
}
