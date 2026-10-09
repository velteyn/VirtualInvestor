package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.BotStrategy;
import com.virtualinvestor.core.bot.OrderIntent;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.ParticipantType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Book-replenishment 2.1: market takes size to visible depth, never into emptiness. */
class DepthAwareTakeTest {

    private static BotRunner runner(BotStrategy strategy) {
        return new BotRunner(
                new BotParticipant("t1", "TIDE", ParticipantType.RIVAL_BOT, 1_000_000.0),
                strategy, null, 0L, 0.01, 0L);
    }

    private static void rest(InstrumentEngine engine, String id, OrderSide side, long qty, double price) {
        engine.submitWithFills(
                new IncomingOrder(id, "m", "ACME", side, OrderType.LIMIT, qty, price), 0L);
    }

    private static List<GameEvent> act(BotRunner runner, InstrumentEngine engine) {
        return runner.act(engine, 100.0, 0.0, 0.0, 0L, 1L, 1_000_000.0, 0.0, null, fill -> {
        });
    }

    @Test
    void signalIntoEmptyBookEmitsNoOrder() {
        BotRunner runner = runner(context -> List.of(OrderIntent.market(OrderSide.BUY, 30L)));
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> events = act(runner, engine);

        assertTrue(events.isEmpty(), "no touch, no take — never burn a reject");
        assertEquals(0, engine.book().orderCount());
    }

    @Test
    void takeSizesToVisibleDepth() {
        BotRunner runner = runner(context -> List.of(OrderIntent.market(OrderSide.BUY, 30L)));
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        rest(engine, "ask-1", OrderSide.SELL, 10L, 101.0);

        List<GameEvent> events = act(runner, engine);

        long filled = events.stream()
                .filter(GameEvent.TradePrinted.class::isInstance)
                .mapToLong(event -> ((GameEvent.TradePrinted) event).quantity())
                .sum();
        assertEquals(10L, filled, "takes the 10 resting, leaves the other 20 unsent");
        assertTrue(events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .noneMatch(report -> report.event()
                        == com.virtualinvestor.core.domain.OrderEventKind.REJECTED),
                "a sized take never rejects");
    }

    @Test
    void blownOutSpreadStandsAside() {
        BotRunner runner = runner(context -> List.of(OrderIntent.market(OrderSide.BUY, 30L)));
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        rest(engine, "bid-1", OrderSide.BUY, 50L, 50.0);
        rest(engine, "ask-1", OrderSide.SELL, 50L, 200.0);

        List<GameEvent> events = act(runner, engine);

        assertTrue(events.isEmpty(), "a 120% spread is deep fade: stand aside");
    }

    @Test
    void limitOrdersPassThroughUntouched() {
        BotRunner runner = runner(context -> List.of(OrderIntent.limit(OrderSide.BUY, 30L, 90.0)));
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);

        List<GameEvent> events = act(runner, engine);

        assertEquals(1, engine.book().orderCount(), "resting orders never reject");
        assertTrue(events.stream()
                .filter(GameEvent.OrderReport.class::isInstance)
                .map(GameEvent.OrderReport.class::cast)
                .noneMatch(report -> report.event()
                        == com.virtualinvestor.core.domain.OrderEventKind.REJECTED),
                "no reject for a resting order");
    }
}
