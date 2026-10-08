package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.persist.player.PlayerStore;
import com.virtualinvestor.runtime.RunningRound;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/** Player-accounts 4.1: settlement writes the purse back; casual policy resets the next round. */
class GameServicePurseTest {

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

    @Test
    void settlementWritesBackAndNextRoundResets() {
        PlayerService players = new PlayerService(store);
        GameService game = new GameService(new RoundRuntimeService(new RoundBroadcaster(
                new com.virtualinvestor.edge.ws.MarketBroadcaster())), players, dataSource);
        String name = "purse-" + UUID.randomUUID().toString().substring(0, 8);
        String playerId = store.register(name, "secret-1", "first-ledger").playerId();
        store.approve(playerId);
        LaunchConfig config = new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC);

        // A stale purse is reset to starting capital when the round is created.
        store.savePurse(playerId, 50_000.0, 9.0,
                Map.of("NVDO", PlayerStore.holding("NVDO", 7L, 600.0)));
        GameService.RoundView first = game.createRound(config, playerId, false, 60_000L);
        RunningRound live = game.runningRound(first.roundId()).orElseThrow();
        assertEquals(100_000.0, live.participant("human").account().cash(), 0.01);
        assertEquals(100_000.0, store.loadPurse(playerId).cash(), 0.01);

        // The human buys, then the round settles early and writes the purse back.
        for (int i = 0; i < 4; i++) {
            live.step();
        }
        live.enqueue(new Command.PlaceOrder("buy-1", "human", "NVDO", OrderSide.BUY, OrderType.MARKET, 5L,
                0.0, 0.0, 0L));
        live.step();
        double midCash = live.participant("human").account().cash();
        assertTrue(midCash < 100_000.0, "the market buy should have moved cash, got " + midCash);
        game.settleEarly(first.roundId());

        double worldCash = live.participant("human").account().cash();
        PlayerStore.Purse saved = store.loadPurse(playerId);
        assertEquals(worldCash, saved.cash(), 0.01, "settlement writes the purse back");
        assertTrue(saved.holdings().isEmpty(), "intraday settlement flattens holdings");

        // Round two under the casual policy starts from starting capital, not the result.
        GameService.RoundView second = game.createRound(config, playerId, false, 60_000L);
        RunningRound live2 = game.runningRound(second.roundId()).orElseThrow();
        assertEquals(100_000.0, live2.participant("human").account().cash(), 0.01);
        assertEquals(100_000.0, store.loadPurse(playerId).cash(), 0.01);
    }

    @Test
    void wiredBotsPayTheirBanksAfterTrading() {
        PlayerService players = new PlayerService(store);
        GameService game = new GameService(new RoundRuntimeService(new RoundBroadcaster(
                new com.virtualinvestor.edge.ws.MarketBroadcaster())), players, dataSource);
        GameService.RoundView view = game.createRound(
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), java.util.List.<String>of(),
                false, 60_000L);
        RunningRound live = game.runningRound(view.roundId()).orElseThrow();
        for (int i = 0; i < 10; i++) {
            live.step();
        }

        double botFees = live.participants().stream()
                .filter(p -> !p.id().equals("human"))
                .mapToDouble(p -> p.account().feesPaid())
                .sum();
        assertTrue(botFees > 0.0, "bots must pay their banks once wired, got " + botFees);
    }
}
