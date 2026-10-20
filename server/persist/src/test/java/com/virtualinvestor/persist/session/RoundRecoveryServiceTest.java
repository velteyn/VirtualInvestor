package com.virtualinvestor.persist.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.JdbcEventLog;
import com.virtualinvestor.persist.event.SequencedEvent;
import com.virtualinvestor.persist.snapshot.JdbcSnapshotStore;
import com.virtualinvestor.persist.snapshot.Recovery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class RoundRecoveryServiceTest {

    private static final String ROUND = "test-recovery";

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

    private static JdbcEventLog log() {
        return new JdbcEventLog(dataSource, ROUND);
    }

    @Test
    void checkpointPrunesTheConsumedPrefixButRecoveryStaysWhole() {
        JdbcEventLog log = log();
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        writer.appendAll(List.of(
                new GameEvent.SessionOpened(ROUND, 1L),
                new GameEvent.Tick(2L, 200L),
                new GameEvent.TradePrinted("ACME", 100.0, 10L, OrderSide.BUY, 2L)));
        writer.flush();

        new RoundRecoveryService(log, snapshots, writer).checkpoint(2L);

        // Ticks below the snapshot are compacted, but the boundary row (for
        // numbering) and the fill row (for fill history) stay by design.
        List<SequencedEvent> remaining = log.readFrom(1L);
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().noneMatch(s -> s.event() instanceof GameEvent.Tick),
                "consumed ticks are pruned");
        assertTrue(remaining.stream().anyMatch(s -> s.event() instanceof GameEvent.TradePrinted),
                "fills stay for the fill-history query");
        RoundRecoveryService.RecoveryResult result = new RoundRecoveryService(
                log(), snapshots, new BatchedEventLogWriter(log(), 100)).recover();
        assertEquals(3, result.history().size(), "snapshot still carries the prefix");
    }

    @Test
    void restartMidRoundRecoversWithoutLossOrDuplication() {
        JdbcEventLog log = log();
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);

        writer.appendAll(List.of(
                new GameEvent.SessionOpened(ROUND, 1L),
                new GameEvent.Tick(2L, 200L),
                new GameEvent.TradePrinted("ACME", 100.0, 10L, OrderSide.BUY, 2L),
                new GameEvent.Tick(3L, 300L),
                new GameEvent.TradePrinted("ACME", 100.5, 5L, OrderSide.SELL, 3L)));
        // Simulate a restart: a fresh service rebuilds from snapshot + log.
        RoundRecoveryService service = new RoundRecoveryService(log, snapshots, writer);
        service.checkpoint(3L);

        RoundRecoveryService recovered = new RoundRecoveryService(
                log(), snapshots, new BatchedEventLogWriter(log(), 100));
        RoundRecoveryService.RecoveryResult result = recovered.recover();

        assertEquals(5, result.history().size(), "no event lost or duplicated");
        assertEquals(3L, result.recoveredTick(), "clock resumes at the recovered tick");
        assertEquals(RoundState.RUNNING, result.state());

        // Continue after recovery and confirm the log stays ordered (it need
        // not restart at 1, nor stay gapless: checkpoints compact the consumed
        // prefix, pruning ticks while fills stay for history).
        BatchedEventLogWriter after = new BatchedEventLogWriter(log(), 100);
        after.append(new GameEvent.Tick(4L, 400L));
        after.flush();
        List<SequencedEvent> all = log().readFrom(1L);
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).seq() < all.get(i).seq(), "log stays ordered");
        }
    }

    @Test
    void pausedRoundRecoversStillPaused() {
        JdbcEventLog log = log();
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);
        RoundRecoveryService service = new RoundRecoveryService(log, snapshots, new BatchedEventLogWriter(log, 100));

        service.writer().append(new GameEvent.SessionOpened(ROUND, 1L));
        service.pause(ROUND, 2L);

        RoundRecoveryService.RecoveryResult result = new RoundRecoveryService(
                log(), snapshots, new BatchedEventLogWriter(log(), 100)).recover();
        assertEquals(RoundState.PAUSED, result.state());
        assertTrue(result.history().stream().anyMatch(GameEvent.RoundPaused.class::isInstance));

        RoundRecoveryService resumed = new RoundRecoveryService(
                log(), snapshots, new BatchedEventLogWriter(log(), 100));
        resumed.resume(ROUND, 3L);
        assertEquals(RoundState.RUNNING, resumed.recover().state());
    }
}
