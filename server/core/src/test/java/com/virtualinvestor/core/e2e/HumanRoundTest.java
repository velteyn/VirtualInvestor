package com.virtualinvestor.core.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.MarginManager;
import com.virtualinvestor.core.game.RivalRoster;
import com.virtualinvestor.core.game.Settlement;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.core.participant.TradeGateway;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end human round (task 12.2): the player trades against the five rivals through the
 * same gateway the bots use, then settlement ranks everyone. This exercises the write path
 * (order → fill → position → P&L) and the ranking, headlessly; the interactive DOM/ticket path
 * is the terminal's job.
 */
class HumanRoundTest {

    private static final double CAPITAL = 100_000.0;

    @Test
    void humanTradesMarketAndSettlesAgainstRivals() {
        TradeGateway gateway = new TradeGateway();
        InstrumentEngine engine = new InstrumentEngine("NVDO", 640.0);
        gateway.register(engine);
        // A maker provides liquidity on both sides.
        engine.rest(new RestingOrder("m-bid", "MARK", "NVDO", OrderSide.BUY, 639.8, 1_000L));
        engine.rest(new RestingOrder("m-ask", "MARK", "NVDO", OrderSide.SELL, 640.2, 1_000L));

        HumanParticipant player = new HumanParticipant("human", "You", CAPITAL);
        List<BotParticipant> rivals = RivalRoster.rivals(CAPITAL);

        // The player buys 100 at the ask, then sells 100 higher.
        List<GameEvent> buyEvents = player.placeOrder(gateway,
                new IncomingOrder("h-1", "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 100L, 0.0), 1L);
        applyFills(player, buyEvents);

        engine.rest(new RestingOrder("m-bid2", "MARK", "NVDO", OrderSide.BUY, 645.0, 1_000L));
        List<GameEvent> sellEvents = player.placeOrder(gateway,
                new IncomingOrder("h-2", "human", "NVDO", OrderSide.SELL, OrderType.MARKET, 100L, 0.0), 2L);
        applyFills(player, sellEvents);

        assertTrue(player.account().position("NVDO").isFlat(), "position closed");
        assertTrue(player.account().realizedPnl() > 0.0, "the round trip should profit");

        // Settlement marks everyone at the close and ranks.
        Map<String, Double> closing = Map.of("NVDO", engine.lastPrice());
        Map<String, Double> capitals = new LinkedHashMap<>();
        capitals.put("human", CAPITAL);
        for (BotParticipant rival : rivals) {
            capitals.put(rival.id(), CAPITAL);
        }
        List<Participant> everyone = new ArrayList<>();
        everyone.add(player);
        everyone.addAll(rivals);
        Settlement.Result result = Settlement.settle(everyone, closing, capitals, 100L, "round-human");

        assertEquals(6, result.standings().size());
        assertTrue(result.standings().stream().anyMatch(s -> s.participantId().equals("human")));
        Standing human = result.standings().stream().filter(s -> s.participantId().equals("human")).findFirst().orElseThrow();
        assertTrue(human.equity() > CAPITAL, "human should be ahead after a profitable round trip");
    }

    @Test
    void marginCallLiquidatesALeveredHuman() {
        HumanParticipant player = new HumanParticipant("human", "You", CAPITAL);
        // 2x leverage long; price collapses and equity falls below maintenance.
        player.account().applyFill("NVDO", OrderSide.BUY, 300L, 640.0);
        MarginManager margin = new MarginManager(0.25);

        List<GameEvent> events = margin.enforce(player, Map.of("NVDO", 400.0), 50L);

        assertTrue(events.stream().anyMatch(GameEvent.MarginCallIssued.class::isInstance));
        assertTrue(player.account().position("NVDO").isFlat());
    }

    /**
     * Apply the taker's own fills from an order's events. Trade prints carry the aggressor's
     * side, so they drive the account update; maker fills (other ids) are skipped.
     */
    private static void applyFills(Participant participant, List<GameEvent> events) {
        for (GameEvent event : events) {
            if (event instanceof GameEvent.TradePrinted print) {
                participant.account().applyFill(print.symbol(), print.aggressorSide(), print.quantity(), print.price());
            }
        }
    }
}
