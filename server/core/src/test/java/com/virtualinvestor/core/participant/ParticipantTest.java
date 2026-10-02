package com.virtualinvestor.core.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipantTest {

    private static List<String> tradeSummary(Participant participant) {
        TradeGateway gateway = new TradeGateway();
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.rest(new RestingOrder("ask1", "maker", "ACME", OrderSide.SELL, 100.0, 30L));
        gateway.register(engine);

        List<GameEvent> events = participant.placeOrder(
                gateway,
                new IncomingOrder("o1", participant.id(), "ACME", OrderSide.BUY, OrderType.MARKET, 10L, 0.0),
                1L);

        return events.stream()
                .filter(GameEvent.TradePrinted.class::isInstance)
                .map(event -> {
                    GameEvent.TradePrinted print = (GameEvent.TradePrinted) event;
                    return print.price() + "/" + print.quantity() + "/" + print.aggressorSide();
                })
                .toList();
    }

    @Test
    void humanAndBotTradeIdentically() {
        HumanParticipant human = new HumanParticipant("h1", "You", 100_000L);
        BotParticipant bot = new BotParticipant("b1", "TIDE", ParticipantType.RIVAL_BOT, 100_000L);

        List<String> humanTrades = tradeSummary(human);
        List<String> botTrades = tradeSummary(bot);

        assertEquals(List.of("100.0/10/BUY"), humanTrades);
        assertEquals(humanTrades, botTrades);
    }

    @Test
    void gatewayRefusesSpoofedParticipant() {
        TradeGateway gateway = new TradeGateway();
        gateway.register(new InstrumentEngine("ACME", 100.0));
        HumanParticipant human = new HumanParticipant("h1", "You", 100_000L);

        IncomingOrder spoofed = new IncomingOrder("o1", "someone-else", "ACME", OrderSide.BUY, OrderType.MARKET, 1L, 0.0);
        assertThrows(IllegalArgumentException.class, () -> gateway.submit(human, spoofed, 1L));
    }

    @Test
    void botCannotBeHuman() {
        assertThrows(IllegalArgumentException.class,
                () -> new BotParticipant("x", "X", ParticipantType.HUMAN, 1000L));
    }
}
