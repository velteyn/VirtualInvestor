package com.virtualinvestor.core.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.OrderSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class RivalStrategiesTest {

    private static BotContext context(double lastPrice, double fundamental, double priceChange, NewsSignal news) {
        return new BotContext("ACME", lastPrice, fundamental, priceChange, 0.0, 0L, 100_000.0, news, 1L);
    }

    private static OrderSide onlySide(List<OrderIntent> intents) {
        assertEquals(1, intents.size());
        return intents.get(0).side();
    }

    @Test
    void valueBuysUndervaluedAndSellsOvervalued() {
        ValueStrategy value = new ValueStrategy(0.01, 10L);
        assertEquals(OrderSide.BUY, onlySide(value.decide(context(95.0, 100.0, 0.0, null))));
        assertEquals(OrderSide.SELL, onlySide(value.decide(context(105.0, 100.0, 0.0, null))));
        assertTrue(value.decide(context(100.0, 100.0, 0.0, null)).isEmpty());
    }

    @Test
    void momentumFollowsTheMove() {
        MomentumStrategy momentum = new MomentumStrategy(0.5, 10L);
        assertEquals(OrderSide.BUY, onlySide(momentum.decide(context(100.0, 100.0, 1.0, null))));
        assertEquals(OrderSide.SELL, onlySide(momentum.decide(context(100.0, 100.0, -1.0, null))));
        assertTrue(momentum.decide(context(100.0, 100.0, 0.0, null)).isEmpty());
    }

    @Test
    void scalperFadesTheMove() {
        ScalperStrategy scalper = new ScalperStrategy(0.5, 10L);
        assertEquals(OrderSide.SELL, onlySide(scalper.decide(context(100.0, 100.0, 1.0, null))));
        assertEquals(OrderSide.BUY, onlySide(scalper.decide(context(100.0, 100.0, -1.0, null))));
    }

    @Test
    void newsHunterPouncesOnHeadlines() {
        NewsHunterStrategy hunter = new NewsHunterStrategy(0.01, 10L);
        NewsSignal bullish = new NewsSignal(0.05, "ACME", "Tech", NewsScope.COMPANY);
        NewsSignal bearish = new NewsSignal(-0.05, "ACME", "Tech", NewsScope.COMPANY);

        assertEquals(OrderSide.BUY, onlySide(hunter.decide(context(100.0, 100.0, 0.0, bullish))));
        assertEquals(OrderSide.SELL, onlySide(hunter.decide(context(100.0, 100.0, 0.0, bearish))));
        assertTrue(hunter.decide(context(100.0, 100.0, 0.0, null)).isEmpty());
    }
}
