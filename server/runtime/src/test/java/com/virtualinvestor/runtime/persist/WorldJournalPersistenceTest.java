package com.virtualinvestor.runtime.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.JdbcEventLog;
import com.virtualinvestor.persist.event.SequencedEvent;
import com.virtualinvestor.persist.session.RoundRecoveryService;
import com.virtualinvestor.persist.snapshot.JdbcSnapshotStore;
import com.virtualinvestor.runtime.JournaledWorldJournal;
import com.virtualinvestor.runtime.RunningRound;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/** Group 4: the loop's events persist to the log, and checkpoints support recovery. */
class WorldJournalPersistenceTest {

    private static final String ROUND = "round-journal";
    private static final long MINUTE_MILLIS = 60_000L;

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
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM events WHERE round_id = ?")) {
                s.setString(1, ROUND);
                s.executeUpdate();
            }
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM snapshots WHERE round_id = ?")) {
                s.setString(1, ROUND);
                s.executeUpdate();
            }
        }
    }

    @Test
    void eventsPersistInOrderAndCheckpointSupportsRecovery() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);
        RoundRecoveryService recovery = new RoundRecoveryService(log, snapshots, writer);
        JournaledWorldJournal journal = new JournaledWorldJournal(writer, recovery, 5L);

        RunningRound round = new RunningRound(ROUND, 51L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.setJournal(journal);
        round.start();
        for (int i = 0; i < 30 && !round.settled(); i++) {
            round.step();
        }
        journal.flush();

        List<SequencedEvent> stored = log.readFrom(1L);
        assertTrue(stored.size() > 0, "events should be persisted");
        for (int i = 1; i < stored.size(); i++) {
            assertEquals(stored.get(i - 1).seq() + 1L, stored.get(i).seq(), "sequence is contiguous");
        }
        // A snapshot was written by the cadence, so recovery does not replay from scratch.
        assertTrue(snapshots.latest().isPresent(), "a checkpoint should exist");

        // Recovery from snapshot + tail equals the snapshotted prefix plus the tail,
        // even though the log itself now holds only the tail (compaction). The
        // boundary row lives in both, so it counts once.
        List<GameEvent> recovered = com.virtualinvestor.persist.snapshot.Recovery.recover(log, snapshots);
        List<GameEvent> prefix = com.virtualinvestor.persist.snapshot.WorldStateCodec.decode(
                snapshots.latest().orElseThrow().state());
        List<GameEvent> tail = stored.stream().map(SequencedEvent::event).toList();
        assertEquals(prefix.size() + tail.size() - 1, recovered.size());
        assertEquals(tail.subList(1, tail.size()), recovered.subList(prefix.size(), recovered.size()));
    }
}
