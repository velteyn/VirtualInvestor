package com.virtualinvestor.core.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Leaderboard;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.ParticipantType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstitutionStrategyTest {

    @Test
    void vwapSlicesSumToTheParent() {
        VwapExecution execution = new VwapExecution(1000L, 3);
        long total = 0L;
        for (int i = 0; i < 3; i++) {
            total += execution.nextSliceQuantity();
        }
        assertEquals(1000L, total);
        assertTrue(execution.complete());
        assertEquals(0L, execution.nextSliceQuantity());
    }

    @Test
    void vwapExecutionMovesThePrice() {
        InstrumentEngine engine = new InstrumentEngine("ACME", 100.0);
        engine.rest(new RestingOrder("a1", "maker", "ACME", OrderSide.SELL, 100.5, 100L));
        engine.rest(new RestingOrder("a2", "maker", "ACME", OrderSide.SELL, 101.0, 100L));
        engine.rest(new RestingOrder("a3", "maker", "ACME", OrderSide.SELL, 101.5, 100L));

        VwapExecution execution = new VwapExecution(300L, 3);
        int slice = 0;
        while (!execution.complete()) {
            long quantity = execution.nextSliceQuantity();
            engine.submit(new IncomingOrder("child-" + slice, "inst", "ACME",
                    OrderSide.BUY, OrderType.MARKET, quantity, 0.0), ++slice);
        }

        assertEquals(101.5, engine.lastPrice(), 0.0, "institution buying should lift the price");
    }

    @Test
    void macroStrategyOnlyActsOnMacroNews() {
        MacroReactionStrategy institution = new MacroReactionStrategy(0.01, 1000L);
        BotContext macro = context(new NewsSignal(0.05, null, null, NewsScope.MACRO, 1L, Long.MAX_VALUE));
        BotContext company = context(new NewsSignal(0.05, "ACME", "Tech", NewsScope.COMPANY, 1L, Long.MAX_VALUE));

        assertEquals(OrderSide.BUY, institution.decide(macro).get(0).side());
        assertTrue(institution.decide(company).isEmpty());
    }

    @Test
    void leaderboardExcludesInstitutionsAndRanksByEquity() {
        HumanParticipant human = new HumanParticipant("h1", "You", 100_000.0);
        BotParticipant rival = new BotParticipant("b1", "TIDE", ParticipantType.RIVAL_BOT, 120_000.0);
        BotParticipant institution = new BotParticipant("i1", "ATLAS", ParticipantType.INSTITUTION, 5_000_000.0);

        List<Participant> ranked = Leaderboard.ranked(
                List.of(human, rival, institution), Map.of());

        assertEquals(List.of("b1", "h1"), ranked.stream().map(Participant::id).toList());
        assertFalse(ranked.stream().anyMatch(p -> p.type() == ParticipantType.INSTITUTION));
    }

    private static BotContext context(NewsSignal news) {
        return new BotContext("ACME", 100.0, 100.0, 0.0, 0.0, 0L, 100_000.0, news, 1L);
    }
}
