package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.persist.player.PlayerStore;
import com.virtualinvestor.runtime.RunningRound;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/** Multiplayer-seats 2.1: join-order seats, first-N backfill, name-stable banks. */
class GameServiceSeatsTest {

    private static PlayerStore store;
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void migrate() {
        String url = System.getProperty("vi.db.url", "jdbc:postgresql://localhost:5432/virtualinvestor");
        String user = System.getProperty("vi.db.user", "vi");
        String password = System.getProperty("vi.db.password", "vi");
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        store = new PlayerStore(dataSource);
    }

    private static GameService game() {
        return new GameService(new RoundRuntimeService(new RoundBroadcaster(
                new com.virtualinvestor.edge.ws.MarketBroadcaster())),
                new PlayerService(store), dataSource);
    }

    private static String player(String bankId) {
        String name = "seat-" + UUID.randomUUID().toString().substring(0, 8);
        String id = store.register(name, "secret-1", bankId).playerId();
        store.approve(id);
        return id;
    }

    @Test
    void identicalJoinsProduceIdenticalSeatMaps() {
        GameService game = game();
        String first = player("meridian");
        String second = player("first-ledger");
        LaunchConfig config = new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC);

        GameService.RoundView one = game.createRound(config, List.of(first, second), false, 60_000L);
        GameService.RoundView two = game.createRound(config, List.of(first, second), false, 60_000L);

        for (GameService.RoundView view : List.of(one, two)) {
            assertTrue(view.roundId().matches("round-\\d+-[0-9a-f]{4}"),
                    "boot-unique ids never collide across restarts: " + view.roundId());
            assertEquals("human", game.seatFor(view.roundId(), first).orElseThrow());
            assertEquals("human-2", game.seatFor(view.roundId(), second).orElseThrow());
            assertTrue(game.seatFor(view.roundId(), "ghost").isEmpty());
            RunningRound live = game.runningRound(view.roundId()).orElseThrow();
            assertEquals(2 + 4 + 3, live.participants().size(), "2 humans + 4 backfill bots + 3 institutions");
            assertTrue(live.participant("human-2").account().cash() == 100_000.0);
        }
    }

    @Test
    void settlementWritesEachSeatToItsOwnPurse() {
        GameService game = game();
        String first = player("first-ledger");
        String second = player("meridian");
        LaunchConfig config = new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC);
        GameService.RoundView view = game.createRound(config, List.of(first, second), false, 60_000L);
        RunningRound live = game.runningRound(view.roundId()).orElseThrow();
        for (int i = 0; i < 4; i++) {
            live.step();
        }
        live.enqueue(new com.virtualinvestor.core.command.Command.PlaceOrder("s1-buy", "human", "NVDO",
                com.virtualinvestor.core.domain.OrderSide.BUY, com.virtualinvestor.core.domain.OrderType.MARKET,
                5L, 0.0, 0.0, 0L));
        live.enqueue(new com.virtualinvestor.core.command.Command.PlaceOrder("s2-buy", "human-2", "NVDO",
                com.virtualinvestor.core.domain.OrderSide.BUY, com.virtualinvestor.core.domain.OrderType.MARKET,
                15L, 0.0, 0.0, 0L));
        live.step();
        game.settleEarly(view.roundId());

        double cash1 = live.participant("human").account().cash();
        double cash2 = live.participant("human-2").account().cash();
        assertNotEquals(cash1, cash2, 0.01, "different sizes must settle differently");
        assertEquals(cash1, store.loadPurse(first).cash(), 0.01);
        assertEquals(cash2, store.loadPurse(second).cash(), 0.01);

        var ranked = game.leaderboard(view.roundId(), live.marks()).stream()
                .map(com.virtualinvestor.core.participant.Standing::participantId).toList();
        assertTrue(ranked.contains("human") && ranked.contains("human-2"),
                "both humans rank, got " + ranked);
        assertEquals(2 + 4, ranked.size(), "ranked seats are humans plus backfill bots");
    }

    @Test
    void botBanksAreNameStableAndHumansPayTheirs() {
        GameService game = game();
        String first = player("apex");
        GameService.RoundView view = game.createRound(
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC),
                List.of(first), false, 60_000L);
        RunningRound live = game.runningRound(view.roundId()).orElseThrow();

        // bot-mark is roster index 0 -> banks()[0] (alphabetical: apex, 0.02/share, 5.00 min).
        assertEquals(20.0, live.feeScheduleOf("rival-1").feeFor(1000L), 1e-9);
        // The human pays their own bank (apex here too).
        assertEquals(20.0, live.feeScheduleOf("human").feeFor(1000L), 1e-9);
    }
}
