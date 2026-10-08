package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Multiplayer-seats 1.1: extra humans trade as distinct, equally funded participants. */
class MultiHumanTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound round(List<HumanParticipant> extras, long seed) {
        return new RunningRound("round-seats", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS, extras);
    }

    @Test
    void twoHumansTradeAsDistinctParticipants() {
        RunningRound round = round(List.of(new HumanParticipant("human-2", "Bo", 100_000.0)), 21L);
        round.start();
        assertEquals(2 + 4 + 3, round.participants().size(), "2 humans + 4 backfill bots + 3 institutions");
        assertEquals(4, round.rivals().size());

        for (int i = 0; i < 4; i++) {
            round.step();
        }
        round.enqueue(new Command.PlaceOrder("h1-buy", "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 5L,
                0.0, 0.0, 0L));
        round.enqueue(new Command.PlaceOrder("h2-buy", "human-2", "NVDO", OrderSide.BUY, OrderType.MARKET, 5L,
                0.0, 0.0, 0L));
        round.step();

        assertTrue(round.participant("human").account().cash() < 100_000.0);
        assertTrue(round.participant("human-2").account().cash() < 100_000.0);
        assertEquals(5L, round.participant("human").account().position("NVDO").netQty());
        assertEquals(5L, round.participant("human-2").account().position("NVDO").netQty());
    }

    @Test
    void humansFillAgainstEachOther() {
        // An empty book: human 1 rests an offer, human 2 lifts it — the maker
        // report must name human 1 as the counterparty, no bot involved.
        com.virtualinvestor.core.engine.InstrumentEngine engine =
                new com.virtualinvestor.core.engine.InstrumentEngine("NVDO", 100.0);
        engine.submit(new com.virtualinvestor.core.book.IncomingOrder("h1-rest", "human", "NVDO",
                com.virtualinvestor.core.domain.OrderSide.SELL,
                com.virtualinvestor.core.domain.OrderType.LIMIT, 10L, 100.0), 1L);
        var events = engine.submit(new com.virtualinvestor.core.book.IncomingOrder("h2-take", "human-2",
                "NVDO", com.virtualinvestor.core.domain.OrderSide.BUY,
                com.virtualinvestor.core.domain.OrderType.MARKET, 10L, 0.0), 2L);

        var makerFills = events.stream()
                .filter(com.virtualinvestor.core.event.GameEvent.OrderReport.class::isInstance)
                .map(com.virtualinvestor.core.event.GameEvent.OrderReport.class::cast)
                .filter(r -> r.orderId().equals("h1-rest"))
                .toList();
        assertEquals(1, makerFills.size());
        assertEquals(com.virtualinvestor.core.domain.OrderStatus.FILLED, makerFills.get(0).status());
        assertEquals(10L, makerFills.get(0).filledQuantity());
    }

    @Test
    void soloRosterIsUnchanged() {
        RunningRound round = round(List.of(), 22L);

        assertEquals(1 + 5 + 3, round.participants().size());
        assertEquals(5, round.rivals().size());
        assertEquals("MARK", round.rivals().get(0).displayName());
    }

    @Test
    void fullHouseHasNoRivalBots() {
        RunningRound round = round(List.of(
                new HumanParticipant("human-2", "Bo", 100_000.0),
                new HumanParticipant("human-3", "Cy", 100_000.0),
                new HumanParticipant("human-4", "Di", 100_000.0),
                new HumanParticipant("human-5", "Eli", 100_000.0),
                new HumanParticipant("human-6", "Fin", 100_000.0)), 23L);
        round.start();

        assertTrue(round.rivals().isEmpty());
        assertEquals(6 + 3, round.participants().size());
    }

    @Test
    void seventhHumanDoesNotFitSixSeats() {
        assertThrows(IllegalArgumentException.class, () -> round(List.of(
                new HumanParticipant("human-2", "Bo", 100_000.0),
                new HumanParticipant("human-3", "Cy", 100_000.0),
                new HumanParticipant("human-4", "Di", 100_000.0),
                new HumanParticipant("human-5", "Eli", 100_000.0),
                new HumanParticipant("human-6", "Fin", 100_000.0),
                new HumanParticipant("human-7", "Gus", 100_000.0)), 24L));
    }

    @Test
    void joinThenStartBackfillsRoster() {
        RunningRound round = round(List.of(), 25L);
        round.hold();
        assertEquals(com.virtualinvestor.core.domain.RoundState.WAITING, round.state());

        round.joinHuman(new HumanParticipant("human-2", "Bo", 100_000.0));
        assertEquals(2 + 4 + 3, round.participants().size(), "2 humans + 4 backfill bots + 3 institutions");
        assertEquals(4, round.rivals().size());
        assertEquals("BLITZ", round.rivals().get(3).displayName(), "VULTURE yields first");

        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        round.enqueue(new Command.PlaceOrder("h1-buy", "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 5L,
                0.0, 0.0, 0L));
        round.enqueue(new Command.PlaceOrder("h2-buy", "human-2", "NVDO", OrderSide.BUY, OrderType.MARKET, 5L,
                0.0, 0.0, 0L));
        round.step();
        assertEquals(5L, round.participant("human").account().position("NVDO").netQty());
        assertEquals(5L, round.participant("human-2").account().position("NVDO").netQty());

        assertThrows(IllegalStateException.class,
                () -> round.joinHuman(new HumanParticipant("human-3", "Cy", 100_000.0)),
                "roster locks once the clock runs");
    }

    @Test
    void fullRoomRefusesJoin() {
        RunningRound round = round(List.of(), 26L);
        for (int i = 2; i <= 6; i++) {
            round.joinHuman(new HumanParticipant("human-" + i, "P" + i, 100_000.0));
        }
        assertTrue(round.rivals().isEmpty());
        assertEquals(6 + 3, round.participants().size());
        assertThrows(IllegalStateException.class,
                () -> round.joinHuman(new HumanParticipant("human-7", "Gus", 100_000.0)),
                "six seats is six seats");
    }
}
