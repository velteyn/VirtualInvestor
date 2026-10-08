package com.virtualinvestor.persist.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class PlayerStoreTest {

    private static PGSimpleDataSource dataSource;
    private static PlayerStore store;

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

    @BeforeEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM players WHERE username LIKE 'test-%' OR username LIKE 'bot-%'")) {
            statement.executeUpdate();
        }
    }

    @Test
    void registerPendsApprovalWithEmptyPurse() {
        PlayerStore.Player player = store.register("test-ana", "secret-1", "first-ledger");

        assertEquals(PlayerStore.Status.PENDING, player.status());
        assertEquals(PlayerStore.Kind.HUMAN, player.kind());
        assertTrue(store.findByUsername("test-ana").isPresent());
        PlayerStore.Purse purse = store.loadPurse(player.playerId());
        assertEquals(0.0, purse.cash());
        assertTrue(purse.holdings().isEmpty());
    }

    @Test
    void duplicateUsernameRefused() {
        store.register("test-bo", "secret-1", "first-ledger");

        assertThrows(IllegalArgumentException.class, () -> store.register("test-bo", "secret-2", "meridian"));
    }

    @Test
    void unknownBankRefused() {
        assertThrows(IllegalArgumentException.class, () -> store.register("test-cy", "secret-1", "no-such-bank"));
    }

    @Test
    void passwordChecksAndResets() {
        PlayerStore.Player player = store.register("test-di", "secret-1", "meridian");

        assertTrue(store.checkPassword("test-di", "secret-1"));
        assertFalse(store.checkPassword("test-di", "wrong"));
        assertFalse(store.checkPassword("test-nobody", "secret-1"));

        store.resetPassword(player.playerId(), "secret-2");
        assertFalse(store.checkPassword("test-di", "secret-1"));
        assertTrue(store.checkPassword("test-di", "secret-2"));
    }

    @Test
    void approveRejectAndPurseReset() {
        PlayerStore.Player player = store.register("test-eli", "secret-1", "apex");

        store.approve(player.playerId());
        assertEquals(PlayerStore.Status.APPROVED, store.findByUsername("test-eli").orElseThrow().status());
        store.reject(player.playerId());
        assertEquals(PlayerStore.Status.REJECTED, store.findByUsername("test-eli").orElseThrow().status());

        store.savePurse(player.playerId(), 50_000.0, 12.5,
                Map.of("NVDO", PlayerStore.holding("NVDO", 10L, 640.0)));
        store.resetPurse(player.playerId(), 100_000.0);
        PlayerStore.Purse purse = store.loadPurse(player.playerId());
        assertEquals(100_000.0, purse.cash());
        assertEquals(0.0, purse.feesPaid());
        assertTrue(purse.holdings().isEmpty());
    }

    @Test
    void purseRoundTrips() {
        PlayerStore.Player player = store.register("test-fin", "secret-1", "first-ledger");

        store.savePurse(player.playerId(), 90_000.0, 3.25,
                Map.of("NVDO", new PlayerStore.Holding("NVDO", 10L, 640.0, 100.0),
                        "PEAR", new PlayerStore.Holding("PEAR", -5L, 180.0, -20.0)));
        PlayerStore.Purse purse = store.loadPurse(player.playerId());
        assertEquals(90_000.0, purse.cash());
        assertEquals(3.25, purse.feesPaid());
        assertEquals(2, purse.holdings().size());
        assertEquals(10L, purse.holdings().get("NVDO").qty());
        assertEquals(100.0, purse.holdings().get("NVDO").realized());
        assertEquals(-20.0, purse.holdings().get("PEAR").realized());
    }

    @Test
    void ensureBotIsApprovedAndIdempotent() {
        PlayerStore.Player first = store.ensureBot("bot-test", "meridian");
        PlayerStore.Player second = store.ensureBot("bot-test", "meridian");

        assertEquals(PlayerStore.Kind.BOT, first.kind());
        assertEquals(PlayerStore.Status.APPROVED, first.status());
        assertEquals(first.playerId(), second.playerId());
    }

    @Test
    void rosterSeedingCoversEveryRivalAndRerunsClean() {
        List<PlayerStore.Player> first =
                BotSeeder.ensureRoster(store, List.of("first-ledger", "meridian", "apex"));
        List<PlayerStore.Player> second =
                BotSeeder.ensureRoster(store, List.of("first-ledger", "meridian", "apex"));

        assertEquals(5, first.size(), "one BOT row per rival");
        assertTrue(first.stream().allMatch(bot ->
                bot.kind() == PlayerStore.Kind.BOT && bot.status() == PlayerStore.Status.APPROVED));
        assertEquals(
                first.stream().map(PlayerStore.Player::playerId).sorted().toList(),
                second.stream().map(PlayerStore.Player::playerId).sorted().toList(),
                "re-running the seed changes nothing");
    }

    @Test
    void institutionSeedingIsDeterministic() {
        List<PlayerStore.Player> first =
                BotSeeder.ensureInstitutions(store, List.of("apex", "meridian"));
        List<PlayerStore.Player> second =
                BotSeeder.ensureInstitutions(store, List.of("meridian", "apex"));

        assertEquals(3, first.size(), "one BOT row per institution");
        assertEquals(
                first.stream().map(PlayerStore.Player::playerId).sorted().toList(),
                second.stream().map(PlayerStore.Player::playerId).sorted().toList(),
                "bank order must not change who exists");
        assertEquals(
                first.stream().map(PlayerStore.Player::bankId).sorted().toList(),
                List.of("apex", "apex", "meridian"));
    }

    @Test
    void seededBanksAreListed() {
        assertTrue(store.bank("first-ledger").isPresent());
        assertTrue(store.bank("meridian").isPresent());
        assertTrue(store.bank("apex").isPresent());
        assertTrue(store.bank("no-such-bank").isEmpty());
        assertEquals(0.005, store.bank("first-ledger").orElseThrow().feePerShare());
    }

    @Test
    void unknownPlayerRejected() {
        String ghost = UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class, () -> store.loadPurse(ghost));
        assertThrows(IllegalArgumentException.class, () -> store.resetPurse(ghost, 100_000.0));
    }
}
