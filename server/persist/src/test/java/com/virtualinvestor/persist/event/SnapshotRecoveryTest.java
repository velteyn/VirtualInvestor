package com.virtualinvestor.persist.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.snapshot.JdbcSnapshotStore;
import com.virtualinvestor.persist.snapshot.Recovery;
import com.virtualinvestor.persist.snapshot.WorldStateCodec;
import com.virtualinvestor.persist.snapshot.WorldSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class SnapshotRecoveryTest {

    private static final String ROUND = "test-snapshot";

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
    }

    @BeforeEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM events WHERE round_id = ?")) {
                statement.setString(1, ROUND);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM snapshots WHERE round_id = ?")) {
                statement.setString(1, ROUND);
                statement.executeUpdate();
            }
        }
    }

    @Test
    void rebuildFromSnapshotPlusLaterEventsEqualsFullReplay() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);

        List<GameEvent> all = List.of(
                new GameEvent.Tick(1L, 100L),
                new GameEvent.MarketFactorMoved(0.0, 0.01, 1L),
                new GameEvent.Tick(2L, 200L),
                new GameEvent.TradePrinted("ACME", 100.0, 10L, com.virtualinvestor.core.domain.OrderSide.BUY, 2L),
                new GameEvent.Tick(3L, 300L),
                new GameEvent.NewsReleased("n1", "THE_WIRE", com.virtualinvestor.core.domain.NewsScope.MACRO, null, null,
                        "Data beats", "body", 0.01, 100L, com.virtualinvestor.core.domain.NewsStage.RUMOR, 3L,
                        com.virtualinvestor.core.news.ReliabilityTier.WIRE),
                new GameEvent.Tick(4L, 400L));
        writer.appendAll(all);
        writer.flush();

        // Snapshot the state after the first three events.
        List<GameEvent> firstThree = all.subList(0, 3);
        snapshots.save(new WorldSnapshot(2L, 3L, WorldStateCodec.encode(firstThree)));

        List<GameEvent> recovered = Recovery.recover(log, snapshots);

        assertEquals(all, recovered, "snapshot + later events must equal the full log");
        assertTrue(WorldStateCodec.decode(WorldStateCodec.encode(all)).equals(all), "state codec round-trips");
    }

    @Test
    void recoveryWithoutSnapshotReplaysWholeLog() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);
        List<GameEvent> all = List.of(new GameEvent.Tick(1L, 100L), new GameEvent.Tick(2L, 200L));
        writer.appendAll(all);
        writer.flush();

        assertEquals(all, Recovery.recover(log, snapshots));
    }
}
