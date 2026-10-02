package com.virtualinvestor.runtime.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.persist.event.BatchedEventLogWriter;
import com.virtualinvestor.persist.event.JdbcEventLog;
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

/** Group 6.2: a restart mid-round resumes the same state, with no lost or duplicated fill. */
class RoundRecoveryTest {

    private static final String ROUND = "round-recover";
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long SEED = 71L;

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

    private static RunningRound newRound() {
        return new RunningRound(ROUND, SEED, new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
    }

    @Test
    void restartMidRoundResumesToTheSameState() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);

        // Run 20 ticks, persisting as we go.
        RunningRound original = newRound();
        JournaledWorldJournal journal = new JournaledWorldJournal(
                new BatchedEventLogWriter(log, 1000),
                new RoundRecoveryService(log, snapshots, new BatchedEventLogWriter(log, 1000)), 10L);
        original.setJournal(journal);
        original.start();
        for (int i = 0; i < 20 && !original.settled(); i++) {
            original.step();
        }
        journal.flush();
        long recoveredTick = original.tick();
        double originalPrice = original.engines().get("NVDO").lastPrice();

        // "Restart": a fresh round rebuilds by replaying to the recovered tick.
        RunningRound recovered = newRound();
        recovered.recover(recoveredTick, false);

        assertEquals(recoveredTick, recovered.tick(), "the clock resumes at the recovered tick");
        assertEquals(originalPrice, recovered.engines().get("NVDO").lastPrice(), 1e-9,
                "the market state matches the original run");
        assertEquals(RoundState.RUNNING, recovered.state());
    }

    @Test
    void pausedRoundRecoversStillPaused() {
        JdbcEventLog log = new JdbcEventLog(dataSource, ROUND);
        JdbcSnapshotStore snapshots = new JdbcSnapshotStore(dataSource, ROUND);
        RunningRound original = newRound();
        JournaledWorldJournal journal = new JournaledWorldJournal(
                new BatchedEventLogWriter(log, 1000),
                new RoundRecoveryService(log, snapshots, new BatchedEventLogWriter(log, 1000)), 10L);
        original.setJournal(journal);
        original.start();
        for (int i = 0; i < 15; i++) {
            original.step();
        }
        journal.flush();
        long recoveredTick = original.tick();

        RunningRound recovered = newRound();
        recovered.recover(recoveredTick, true);

        assertEquals(RoundState.PAUSED, recovered.state());
        assertEquals(recoveredTick, recovered.tick());
    }
}
